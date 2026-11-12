package com.enderlaserquarry.datagen;

import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The neighbour-dependent parts of a dish or quarry plating top. The blockstate draws the fully joined top and adds every
 * piece whose condition holds; a piece is a few rectangles of its side's texture (<family>_edge_<n|e|s|w>), drawn at the
 * same spot on the block's top. Plain Java, the same on every Minecraft version.
 */
final class ConnectedTop {
    /** Texture pixels, end exclusive; y runs north to south. */
    record Rect(int x0, int y0, int x1, int y1) {
    }

    /** Drawn while {@code side} is open and each listed neighbour is joined (true) or open (false). */
    record Piece(String name, Direction side, Map<Direction, Boolean> neighbours, List<Rect> rects) {
    }

    // the order is the draw order: the east and south lines are dark and win the corners over the light north and west ones
    static List<Piece> pieces(boolean strip) {
        List<Piece> list = new ArrayList<>();
        list.add(side(Direction.NORTH, strip, new Rect(0, 0, 16, 1), new Rect(3, 3, 13, 4)));
        list.add(side(Direction.WEST, strip, new Rect(0, 0, 1, 16), new Rect(3, 3, 4, 13)));
        list.add(side(Direction.EAST, strip, new Rect(15, 0, 16, 16), new Rect(12, 3, 13, 13)));
        list.add(side(Direction.SOUTH, strip, new Rect(0, 15, 16, 16), new Rect(3, 12, 13, 13)));
        if (strip) {
            // the light strip runs on into a joined neighbour
            list.add(piece(Direction.NORTH, Direction.WEST, true, new Rect(0, 3, 3, 4)));
            list.add(piece(Direction.NORTH, Direction.EAST, true, new Rect(13, 3, 16, 4)));
            list.add(piece(Direction.SOUTH, Direction.WEST, true, new Rect(0, 12, 3, 13)));
            list.add(piece(Direction.SOUTH, Direction.EAST, true, new Rect(13, 12, 16, 13)));
            list.add(piece(Direction.WEST, Direction.NORTH, true, new Rect(3, 0, 4, 3)));
            list.add(piece(Direction.WEST, Direction.SOUTH, true, new Rect(3, 13, 4, 16)));
            list.add(piece(Direction.EAST, Direction.NORTH, true, new Rect(12, 0, 13, 3)));
            list.add(piece(Direction.EAST, Direction.SOUTH, true, new Rect(12, 13, 13, 16)));
        }
        // a bolt in each corner whose two sides are open
        list.add(piece(Direction.NORTH, Direction.WEST, false, new Rect(1, 1, 2, 2)));
        list.add(piece(Direction.NORTH, Direction.EAST, false, new Rect(13, 1, 14, 2)));
        list.add(piece(Direction.SOUTH, Direction.WEST, false, new Rect(1, 13, 2, 14)));
        list.add(piece(Direction.SOUTH, Direction.EAST, false, new Rect(13, 13, 14, 14)));
        return list;
    }

    static char letter(Direction side) {
        return side.getSerializedName().charAt(0);
    }

    private static Piece side(Direction side, boolean strip, Rect line, Rect middle) {
        return new Piece(String.valueOf(letter(side)), side, Map.of(), strip ? List.of(line, middle) : List.of(line));
    }

    // strip end ("n_to_w") or corner bolt ("nw")
    private static Piece piece(Direction side, Direction neighbour, boolean joined, Rect rect) {
        String name = joined ? letter(side) + "_to_" + letter(neighbour) : "" + letter(side) + letter(neighbour);
        return new Piece(name, side, Map.of(neighbour, joined), List.of(rect));
    }

    private ConnectedTop() {
    }
}

package com.etheller.warsmash.util;

import com.badlogic.gdx.graphics.Color;

public class ListItemStringProperty extends AbstractListItemProperty {
    private Color color;
    private boolean selectable = true;

    public ListItemStringProperty(String rawValue) {
        super(ListItemEnum.ITEM_STRING, rawValue);
    }

    public ListItemStringProperty(String rawValue, Color color) {
        this(rawValue);
        this.color = color;
    }

    /** A heading of a section: shown in the colour, not to be chosen. */
    public static ListItemStringProperty heading(String title, Color color) {
        final ListItemStringProperty heading = new ListItemStringProperty(title, color);
        heading.selectable = false;
        return heading;
    }

    @Override
    public boolean isSelectable() {
        return selectable;
    }

    /** Null for the list's usual white. */
    public Color getColor() {
        return color;
    }
    
}

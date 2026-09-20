package it.polimi.ingsw.server.model.card;

import it.polimi.ingsw.util.supportclasses.Resource;
import java.util.Objects;

/**
 * This class represents a Corner on the card.
 */
public class Corner {
    protected Resource resource; //resource present in the corner
    protected boolean visible; //true if the corner is visible
    protected boolean attached; //true if it's connected to another corner
    protected boolean attachable; //true if corner is present on the card for connection of other cards on top of it

    public Corner(Resource resource, boolean attachable) {
        this.resource = resource;
        this.attachable = attachable;
        attached = false;
        visible = true;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Corner other)) return false;
        return resource == other.resource && attached == other.attached
                && attachable == other.attachable && visible == other.visible;
    }

    @Override
    public int hashCode() {
        return Objects.hash(resource, visible, attached, attachable);
    }

    public Resource getResource() {
        if(this.isAttachable()) return resource;
        else return Resource.none;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isAttachable() {
        return attachable;
    }

    public boolean isAttached() {
        return attached;
    }

    public void setAttached(boolean attached) {
        this.attached = attached;
    }
}

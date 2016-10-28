package org.openpnp.gui.support;

import org.openpnp.spi.Actuator;
import org.openpnp.spi.Camera;
import org.openpnp.spi.HeadMountable;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.PasteDispenser;

public class HeadMountableItem {
    private HeadMountable hm;
    private String label;
    
    public HeadMountableItem(HeadMountable hm, String label) {
        this.hm = hm;
        this.label = label;
    }

    public HeadMountableItem(HeadMountable hm) {
        this(hm, null);
    }

    public HeadMountable getHeadMountable() {
        return hm;
    }
    
    @Override
    public String toString() {
        if (label != null) {
            return label;
        }
        if (hm == null) {
            return "Default";
        }
        String type = "HeadMountable";
        if (hm instanceof Nozzle) {
            type = "Nozzle";
        }
        else if (hm instanceof PasteDispenser) {
            type = "Paste Dispenser";
        }
        else if (hm instanceof Camera) {
            type = "Camera";
        }
        else if (hm instanceof Actuator) {
            type = "Actuator";
        }
        if (hm.getHead() == null) {
            return String.format("%s: %s", type, hm.getName());
        }
        else {
            return String.format("%s: %s %s", type, hm.getHead().getName(), hm.getName());
        }
    }
}
package org.openpnp.vision.pipeline.stages;

import java.awt.Color;

import org.openpnp.vision.pipeline.CvPipeline;
import org.openpnp.vision.pipeline.CvStage;
import org.openpnp.vision.pipeline.Stage;
import org.openpnp.vision.pipeline.stages.convert.ColorConverter;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.convert.Convert;

@Stage(description="Change the lighting color of camera.")
public class SetCameraLightingColor extends CvStage {
    @Element(required = true)
    @Convert(ColorConverter.class)
    private Color color = null;

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

	
	public SetCameraLightingColor() {
	}

	@Override
	public Result process(CvPipeline pipeline) throws Exception {
		pipeline.getCamera().setLightingColor(color);
		return null;
	}

}

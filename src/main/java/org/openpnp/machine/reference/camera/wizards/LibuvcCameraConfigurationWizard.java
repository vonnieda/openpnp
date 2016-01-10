/*
 	Copyright (C) 2011 Jason von Nieda <jason@vonnieda.org>
 	
 	This file is part of OpenPnP.
 	
	OpenPnP is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    OpenPnP is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with OpenPnP.  If not, see <http://www.gnu.org/licenses/>.
 	
 	For more information about OpenPnP visit http://openpnp.org
*/

package org.openpnp.machine.reference.camera.wizards;

import java.awt.Color;

import javax.swing.JPanel;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.openpnp.gui.support.IntegerConverter;
import org.openpnp.gui.support.JBindings;
import org.openpnp.machine.reference.camera.LibuvcCamera;
import org.openpnp.machine.reference.wizards.ReferenceCameraConfigurationWizard;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;
import javax.swing.JLabel;
import javax.swing.JSlider;

@SuppressWarnings("serial")
public class LibuvcCameraConfigurationWizard extends ReferenceCameraConfigurationWizard {
	private final LibuvcCamera camera;

	private JPanel panelGeneral;
	private JSlider sliderExposure;

	public LibuvcCameraConfigurationWizard(
			LibuvcCamera camera) {
	    super(camera);
	    
		this.camera = camera;

		panelGeneral = new JPanel();
		contentPanel.add(panelGeneral);
		panelGeneral.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null), "General", TitledBorder.LEADING, TitledBorder.TOP, null, new Color(0, 0, 0)));
		panelGeneral.setLayout(new FormLayout(new ColumnSpec[] {
				FormSpecs.RELATED_GAP_COLSPEC,
				FormSpecs.DEFAULT_COLSPEC,
				FormSpecs.RELATED_GAP_COLSPEC,
				FormSpecs.DEFAULT_COLSPEC,
				FormSpecs.RELATED_GAP_COLSPEC,
				FormSpecs.DEFAULT_COLSPEC,},
			new RowSpec[] {
				FormSpecs.RELATED_GAP_ROWSPEC,
				FormSpecs.DEFAULT_ROWSPEC,}));
		
		JLabel lblExposure = new JLabel("Exposure");
		panelGeneral.add(lblExposure, "2, 2");
		
		sliderExposure = new JSlider();
		sliderExposure.setMaximum(1000);
		panelGeneral.add(sliderExposure, "4, 2");
	}

	@Override
	public void createBindings() {
		bind(UpdateStrategy.READ_WRITE, camera, "exposure", sliderExposure, "value");
	    super.createBindings();
	}
}
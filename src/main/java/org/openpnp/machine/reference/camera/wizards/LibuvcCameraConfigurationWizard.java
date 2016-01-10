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
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.openpnp.machine.reference.camera.LibuvcCamera;
import org.openpnp.machine.reference.camera.LibuvcCamera.CameraId;
import org.openpnp.machine.reference.wizards.ReferenceCameraConfigurationWizard;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;

@SuppressWarnings("serial")
public class LibuvcCameraConfigurationWizard extends ReferenceCameraConfigurationWizard {
	private final LibuvcCamera camera;

	private JPanel panelGeneral;
	private JSlider sliderExposure;
	private JLabel lblDevice;
	private JComboBox comboBoxCameraId;

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
				ColumnSpec.decode("default:grow"),
				FormSpecs.RELATED_GAP_COLSPEC,
				FormSpecs.DEFAULT_COLSPEC,},
			new RowSpec[] {
				FormSpecs.RELATED_GAP_ROWSPEC,
				FormSpecs.DEFAULT_ROWSPEC,
				FormSpecs.RELATED_GAP_ROWSPEC,
				FormSpecs.DEFAULT_ROWSPEC,}));
		
		lblDevice = new JLabel("Camera ID");
		panelGeneral.add(lblDevice, "2, 2, right, default");
		
		comboBoxCameraId = new JComboBox<CameraId>();
		panelGeneral.add(comboBoxCameraId, "4, 2, fill, default");
		
		JLabel lblExposure = new JLabel("Exposure");
		panelGeneral.add(lblExposure, "2, 4");
		
		sliderExposure = new JSlider();
		sliderExposure.setPaintTicks(true);
		sliderExposure.setPaintLabels(true);
		sliderExposure.setMinimum(1);
		sliderExposure.setMaximum(10000);
		panelGeneral.add(sliderExposure, "4, 4");
		
		comboBoxCameraId.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                refreshCameraIdList();
            }
            
            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }
            
            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
		refreshCameraIdList();
	}
	
    private void refreshCameraIdList() {
    	comboBoxCameraId.removeAllItems();
        boolean exists = false;
        List<CameraId> cameras = camera.getCameraIds();
        for (CameraId camera : cameras) {
        	comboBoxCameraId.addItem(camera);
//            if (portName.equals(driver.getPortName())) {
//                exists = true;
//            }
        }
//        if (!exists && driver.getPortName() != null) {
//            comboBoxPort.addItem(driver.getPortName());
//        }
    }


	@Override
	public void createBindings() {
		bind(UpdateStrategy.READ_WRITE, camera, "exposure", sliderExposure, "value");
		addWrappedBinding(camera, "cameraId", comboBoxCameraId, "selectedItem");
	    super.createBindings();
	}
}
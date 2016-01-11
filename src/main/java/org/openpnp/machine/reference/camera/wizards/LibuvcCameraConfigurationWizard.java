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

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.openpnp.gui.components.ComponentDecorators;
import org.openpnp.gui.support.IntegerConverter;
import org.openpnp.libuvc4j.UvcLibrary.uvc_frame_format;
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
	private JSlider sliderManualExposureTime;
	private JLabel lblDevice;
	private JComboBox<CameraId> comboBoxCameraId;
	private JCheckBox chckbxManualExposureEnabled;
	private JLabel labelManualExposureTime;
	private JLabel lblNewLabel;
	private JPanel panel;
	private JLabel lblWidth;
	private JLabel lblHeight;
	private JTextField textFieldResolutionX;
	private JTextField textFieldResolutionY;
	private JLabel lblFormat;
	private JComboBox<uvc_frame_format> comboBoxFrameFormat;
	private JLabel lblFps;
	private JTextField textFieldFps;

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
				FormSpecs.DEFAULT_ROWSPEC,
				FormSpecs.RELATED_GAP_ROWSPEC,
				RowSpec.decode("default:grow"),
				FormSpecs.RELATED_GAP_ROWSPEC,
				FormSpecs.DEFAULT_ROWSPEC,
				FormSpecs.RELATED_GAP_ROWSPEC,
				FormSpecs.DEFAULT_ROWSPEC,
				FormSpecs.RELATED_GAP_ROWSPEC,
				FormSpecs.DEFAULT_ROWSPEC,}));
		
		lblDevice = new JLabel("Camera ID");
		panelGeneral.add(lblDevice, "2, 2, right, default");
		
		comboBoxCameraId = new JComboBox<CameraId>();
		panelGeneral.add(comboBoxCameraId, "4, 2, fill, default");
		
		lblFormat = new JLabel("Frame Format");
		panelGeneral.add(lblFormat, "2, 4, right, default");
		
		comboBoxFrameFormat = new JComboBox<uvc_frame_format>();
		panelGeneral.add(comboBoxFrameFormat, "4, 4, fill, default");
		
		lblNewLabel = new JLabel("Resolution");
		panelGeneral.add(lblNewLabel, "2, 6, right, top");
		
		panel = new JPanel();
		panelGeneral.add(panel, "4, 6, fill, fill");
		panel.setLayout(new FormLayout(new ColumnSpec[] {
				FormSpecs.RELATED_GAP_COLSPEC,
				ColumnSpec.decode("default:grow"),
				FormSpecs.RELATED_GAP_COLSPEC,
				ColumnSpec.decode("default:grow"),},
			new RowSpec[] {
				FormSpecs.RELATED_GAP_ROWSPEC,
				FormSpecs.DEFAULT_ROWSPEC,
				FormSpecs.RELATED_GAP_ROWSPEC,
				FormSpecs.DEFAULT_ROWSPEC,}));
		
		lblWidth = new JLabel("Width");
		panel.add(lblWidth, "2, 2");
		
		lblHeight = new JLabel("Height");
		panel.add(lblHeight, "4, 2");
		
		textFieldResolutionX = new JTextField();
		textFieldResolutionX.setText("0");
		panel.add(textFieldResolutionX, "2, 4, fill, default");
		textFieldResolutionX.setColumns(10);
		
		textFieldResolutionY = new JTextField();
		textFieldResolutionY.setText("0");
		panel.add(textFieldResolutionY, "4, 4, fill, default");
		textFieldResolutionY.setColumns(10);
		
		lblFps = new JLabel("FPS");
		panelGeneral.add(lblFps, "2, 8, right, default");
		
		textFieldFps = new JTextField();
		textFieldFps.setText("30");
		panelGeneral.add(textFieldFps, "4, 8, fill, default");
		textFieldFps.setColumns(10);
		
		JLabel lblExposure = new JLabel("Exposure");
		panelGeneral.add(lblExposure, "2, 10, right, default");
		
		chckbxManualExposureEnabled = new JCheckBox("Manual Exposure Enabled");
		panelGeneral.add(chckbxManualExposureEnabled, "4, 10");
		
		sliderManualExposureTime = new JSlider();
		sliderManualExposureTime.setPaintTicks(true);
		sliderManualExposureTime.setPaintLabels(true);
		sliderManualExposureTime.setMinimum(1);
		sliderManualExposureTime.setMaximum(500);
		panelGeneral.add(sliderManualExposureTime, "4, 12, fill, default");
		
		labelManualExposureTime = new JLabel("0");
		panelGeneral.add(labelManualExposureTime, "6, 12");
		
		// TODO: Leaving this out until we can do it without reseting the
		// selection.
//		comboBoxCameraId.addPopupMenuListener(new PopupMenuListener() {
//            @Override
//            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
//            	// TODO: Would be better to merge, or only refresh if refresh
//            	// is needed. I think we also need to reselect if we refresh.
//                refreshCameraIdList();
//            }
//            
//            @Override
//            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
//            }
//            
//            @Override
//            public void popupMenuCanceled(PopupMenuEvent e) {
//            }
//        });
		
		for (uvc_frame_format format : uvc_frame_format.values()) {
			comboBoxFrameFormat.addItem(format);
		}
		
		refreshCameraIdList();
	}
	
    private void refreshCameraIdList() {
    	comboBoxCameraId.removeAllItems();
        boolean exists = false;
        List<CameraId> ids = camera.getCameraIds(true);
        for (CameraId id : ids) {
        	comboBoxCameraId.addItem(id);
            if (id.equals(camera.getCameraId())) {
                exists = true;
            }
        }
        if (!exists && camera.getCameraId() != null) {
            comboBoxCameraId.addItem(camera.getCameraId());
        }
    }


	@Override
	public void createBindings() {
	    super.createBindings();

	    IntegerConverter intConverter = new IntegerConverter();
        addWrappedBinding(camera, "resolutionX", textFieldResolutionX, "text", intConverter);
        addWrappedBinding(camera, "resolutionY", textFieldResolutionY, "text", intConverter);
		addWrappedBinding(camera, "frameFormat", comboBoxFrameFormat, "selectedItem");
		addWrappedBinding(camera, "fps", textFieldFps, "text", intConverter);
		
		bind(UpdateStrategy.READ_WRITE, chckbxManualExposureEnabled, "selected", sliderManualExposureTime, "enabled");
		bind(UpdateStrategy.READ_WRITE, camera, "manualExposureEnabled", chckbxManualExposureEnabled, "selected");
		bind(UpdateStrategy.READ_WRITE, camera, "manualExposureTime", sliderManualExposureTime, "value");
		bind(UpdateStrategy.READ, sliderManualExposureTime, "value", labelManualExposureTime, "text");
		
		// Should always be last so all parameters get set before opening the camera.
		addWrappedBinding(camera, "cameraId", comboBoxCameraId, "selectedItem");
		
        ComponentDecorators.decorateWithAutoSelect(textFieldResolutionX);
        ComponentDecorators.decorateWithAutoSelect(textFieldResolutionY);
	}
}
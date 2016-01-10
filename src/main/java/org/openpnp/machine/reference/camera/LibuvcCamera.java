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

package org.openpnp.machine.reference.camera;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;

import javax.swing.Action;

import org.bridj.IntValuedEnum;
import org.bridj.Pointer;
import org.openpnp.ConfigurationListener;
import org.openpnp.gui.support.Wizard;
import org.openpnp.libuvc4j.UvcLibrary;
import org.openpnp.libuvc4j.UvcLibrary.uvc_context;
import org.openpnp.libuvc4j.UvcLibrary.uvc_device;
import org.openpnp.libuvc4j.UvcLibrary.uvc_device_handle;
import org.openpnp.libuvc4j.UvcLibrary.uvc_error;
import org.openpnp.libuvc4j.uvc_frame;
import org.openpnp.libuvc4j.uvc_stream_ctrl;
import org.openpnp.machine.reference.ReferenceCamera;
import org.openpnp.machine.reference.camera.wizards.LibuvcCameraConfigurationWizard;
import org.openpnp.model.Configuration;
import org.openpnp.spi.PropertySheetHolder;
import org.simpleframework.xml.Attribute;

/**
 * A Camera implementation based on the OpenCV FrameGrabbers.
 */
public class LibuvcCamera extends ReferenceCamera {
	@Attribute(required=false)
	private int vid = 0;
	
	@Attribute(required=false)
	private int pid = 0;
	
	private Pointer<Pointer<uvc_context>> ctx;
	private Pointer<Pointer<uvc_device>> dev;
	private Pointer<Pointer<uvc_device_handle>> devh;
	private Pointer<uvc_stream_ctrl> ctrl;
	private BufferedImage lastFrame;
	
	public LibuvcCamera() {
		Configuration.get().addListener(new ConfigurationListener.Adapter() {
			@Override
			public void configurationComplete(Configuration configuration) throws Exception {
				connect();
			}
		});
	}
	
	private void connect() {
		IntValuedEnum<uvc_error> err;
		
		ctx = Pointer.allocatePointer(uvc_context.class);
		err = UvcLibrary.uvc_init(ctx, null);
		if (err != uvc_error.UVC_SUCCESS) {
			System.out.println("uvc_init " + err);
			return;
		}
		
		dev = Pointer.allocatePointer(uvc_device.class);
		System.out.println(vid);
		System.out.println(pid);
		err = UvcLibrary.uvc_find_device(ctx.get(), dev, vid, pid, null);
		if (err != uvc_error.UVC_SUCCESS) {
			System.out.println("uvc_find_device " + err);
			return;
		}
		
		devh = Pointer.allocatePointer(uvc_device_handle.class);
		err = UvcLibrary.uvc_open(dev.get(), devh);
		if (err != uvc_error.UVC_SUCCESS) {
			System.out.println("uvc_open " + err);
			return;
		}
		
//		UVC_AUTO_EXPOSURE_MODE_MANUAL (1) - manual exposure time, manual iris
//		UVC_AUTO_EXPOSURE_MODE_AUTO (2) - auto exposure time, auto iris
//		UVC_AUTO_EXPOSURE_MODE_SHUTTER_PRIORITY (4) - manual exposure time, auto iris
//		UVC_AUTO_EXPOSURE_MODE_APERTURE_PRIORITY (8) - auto exposure time, manual iris
		err = UvcLibrary.uvc_set_ae_mode(devh.get(), (byte) 1);
		if (err != uvc_error.UVC_SUCCESS) {
			System.out.println("uvc_set_ae_mode " + err);
			return;
		}
		
		ctrl = Pointer.allocate(uvc_stream_ctrl.class);
		err = UvcLibrary.uvc_get_stream_ctrl_format_size(
		          devh.get(), ctrl,
		          UvcLibrary.uvc_frame_format.UVC_FRAME_FORMAT_MJPEG,
		          640, 480, 30);
		if (err != uvc_error.UVC_SUCCESS) {
			System.out.println("uvc_get_stream_ctrl_format_size " + err);
			return;
		}
		
		err = UvcLibrary.uvc_start_streaming(devh.get(), ctrl, cb.toPointer(), null, (byte) 0);
		if (err != uvc_error.UVC_SUCCESS) {
			System.out.println("uvc_start_streaming " + err);
			return;
		}
	}
	
	UvcLibrary.uvc_frame_callback_t cb = new UvcLibrary.uvc_frame_callback_t() {
		@Override
		public void apply(Pointer<uvc_frame> frame, Pointer<?> user_ptr) {
			BufferedImage image = decodeFrame(frame);
			if (image != null) {
				lastFrame = image;
			}
			broadcastCapture(lastFrame);
		}
	};
	
	private static BufferedImage decodeFrame(Pointer<uvc_frame> frame) {
		// Have to use .equals here instead of == otherwise we always get
		// UVC_FRAME_FORMAT_ANY. Possibly a generation error, but not sure.
		if (frame.get().frame_format().equals(UvcLibrary.uvc_frame_format.UVC_FRAME_FORMAT_MJPEG)) {
			return decodeMjpegFrame(frame);
		}
		else {
			return decodeNonMjpegFrame(frame);
		}
	}
	
	private static BufferedImage decodeNonMjpegFrame(Pointer<uvc_frame> frame) {
		return null;
	}
	
	private static BufferedImage decodeMjpegFrame(Pointer<uvc_frame> frame) {
		IntValuedEnum<UvcLibrary.uvc_error> err;
		int width = frame.get().width();
		int height = frame.get().height();
		
		Pointer<uvc_frame> rgbFrame = UvcLibrary.uvc_allocate_frame(width * height * 3);
		if (rgbFrame == null || rgbFrame.get() == null) {
			System.out.println("uvc_allocate_frame");
			return null;
		}
		
		err = UvcLibrary.uvc_mjpeg2rgb(frame, rgbFrame);
		if (err != UvcLibrary.uvc_error.UVC_SUCCESS) {
			System.out.println("uvc_mjpeg2rgb " + err);
			UvcLibrary.uvc_free_frame(rgbFrame);
			return null;
		}

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
		byte[] dst = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
		byte[] src = rgbFrame.get().data().getBytes(width * height * 3);
		// copy bytes over swapping red and blue to convert from RGB to BGR
		for (int i = 0, count = width * height * 3; i < count; i += 3) {
			dst[i + 0] = src[i + 2];
			dst[i + 1] = src[i + 1];
			dst[i + 2] = src[i + 0];
		}
		
		UvcLibrary.uvc_free_frame(rgbFrame);
		
		return image;
	}
	
	@Override
	public BufferedImage capture() {
		return lastFrame;
	}

	@Override
	public Wizard getConfigurationWizard() {
		return new LibuvcCameraConfigurationWizard(this);
	}

	@Override
	public String getPropertySheetHolderTitle() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PropertySheetHolder[] getChildPropertySheetHolders() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PropertySheet[] getPropertySheets() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Action[] getPropertySheetHolderActions() {
		// TODO Auto-generated method stub
		return null;
	}
	
	public int getExposure() {
		Pointer<Integer> val = Pointer.allocateInt();
		UvcLibrary.uvc_get_exposure_abs(devh.get(), val, UvcLibrary.uvc_req_code.UVC_GET_CUR);
		return val.getInt();
	}
	
	public void setExposure(int val) {
		UvcLibrary.uvc_set_exposure_abs(devh.get(), val);
	}

	@Override
	public void close() throws IOException {
		super.close();
		UvcLibrary.uvc_stop_streaming(devh.get());
		UvcLibrary.uvc_close(devh.get());
		UvcLibrary.uvc_unref_device(dev.get());
		UvcLibrary.uvc_exit(ctx.get());
	}	
}

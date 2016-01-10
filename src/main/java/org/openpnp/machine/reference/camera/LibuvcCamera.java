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
import java.util.ArrayList;
import java.util.List;

import javax.swing.Action;

import org.bridj.IntValuedEnum;
import org.bridj.Pointer;
import org.openpnp.gui.support.Wizard;
import org.openpnp.libuvc4j.UvcLibrary;
import org.openpnp.libuvc4j.UvcLibrary.uvc_context;
import org.openpnp.libuvc4j.UvcLibrary.uvc_device;
import org.openpnp.libuvc4j.UvcLibrary.uvc_device_handle;
import org.openpnp.libuvc4j.UvcLibrary.uvc_error;
import org.openpnp.libuvc4j.uvc_device_descriptor;
import org.openpnp.libuvc4j.uvc_frame;
import org.openpnp.libuvc4j.uvc_stream_ctrl;
import org.openpnp.machine.reference.ReferenceCamera;
import org.openpnp.machine.reference.camera.wizards.LibuvcCameraConfigurationWizard;
import org.openpnp.spi.PropertySheetHolder;
import org.simpleframework.xml.Attribute;

/**
 * TODO:
 * connect on first capture instead of in constructor
 * enable/disable AE
 * AE settings in config and applied during startup
 * camera selection based on list
 * "uniqueness" as a combination of every damn thing we can find
 * 		specifically should handle two ELP cams hooked up
 * fix errors on shutdown when something was not fully initialized
 * add decoding for non mjpeg frames
 * resolution, frame format, fps settings - these can probably be ganged together
 * apply transforms
 * need to think about how much data to store for each ID
 * 		if there are no duplicate IDs (without bus info) we only want to store
 * 		that so that we aren't bus dependent
 * 
 * 		but if we don't store bus info and they plug in a dupe then when we
 * 		start we might not identify correctly. but in that case they will
 * 		see the wrong camera and can reset the ID which will now include
 * 		the non dupe info.
 * 
 * 		so when we build the ID list we need to check for existing and if
 * 		it exists use the more specific ID
 */
public class LibuvcCamera extends ReferenceCamera {
	static private Pointer<Pointer<uvc_context>> ctx;
	static {
		IntValuedEnum<uvc_error> err;
		
		ctx = Pointer.allocatePointer(uvc_context.class);
		err = UvcLibrary.uvc_init(ctx, null);
		if (err != uvc_error.UVC_SUCCESS) {
			throw new Error("uvc_init " + err);
		}
	}

	@Attribute(required=false)
	private int vid = 0;
	
	@Attribute(required=false)
	private int pid = 0;
	
	private Pointer<Pointer<uvc_device>> dev;
	private Pointer<Pointer<uvc_device_handle>> devh;
	private Pointer<uvc_stream_ctrl> ctrl;
	private BufferedImage lastFrame;
	
	// temporary
	private CameraId cameraId;
	
	public LibuvcCamera() {
	}
	
	private void connect() {
		IntValuedEnum<uvc_error> err;
		
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
		if (devh == null) {
			return 0;
		}
		Pointer<Integer> val = Pointer.allocateInt();
		UvcLibrary.uvc_get_exposure_abs(devh.get(), val, UvcLibrary.uvc_req_code.UVC_GET_CUR);
		return val.getInt();
	}
	
	public void setExposure(int val) {
		if (devh == null) {
			return;
		}
		UvcLibrary.uvc_set_exposure_abs(devh.get(), val);
	}
	
	public CameraId getCameraId() {
		return cameraId;
	}
	
	public void setCameraId(CameraId cameraId) {
		this.cameraId = cameraId;
	}
	
	/**
	 * Returns a list of uniquely identifiable camera IDs. 
	 * @return
	 */
	public List<CameraId> getCameraIds() {
		List<CameraId> ids = new ArrayList<>();
		
		IntValuedEnum<uvc_error> err;

		Pointer<Pointer<Pointer<uvc_device>>> devList = Pointer.allocatePointerPointer(uvc_device.class);
		err = UvcLibrary.uvc_get_device_list(ctx.get(), devList);
		if (err != uvc_error.UVC_SUCCESS) {
			throw new Error("uvc_get_device_list " + err);
		}
		
		for (Pointer<uvc_device> dev : devList.get()) {
			if (dev == null) {
				break;
			}
			
			byte busNumber = UvcLibrary.uvc_get_bus_number(dev);
			byte devAddress = UvcLibrary.uvc_get_device_address(dev);
			
			Pointer<Pointer<uvc_device_descriptor>> desc = Pointer.allocatePointer(uvc_device_descriptor.class);
			err = UvcLibrary.uvc_get_device_descriptor(dev, desc);
			if (err != uvc_error.UVC_SUCCESS) {
				throw new Error("uvc_get_device_descriptor " + err);
			}
			
			int vendorId = desc.get().get().idVendor() & 0xffff;
			int productId = desc.get().get().idProduct() & 0xffff;
			String manufacturer = getString(desc.get().get().manufacturer());
			String product = getString(desc.get().get().product());
			String serial = getString(desc.get().get().serialNumber());
			
			CameraId cameraInfo = new CameraId(busNumber, devAddress, 
					vendorId, productId, manufacturer, product, serial);
			
			ids.add(cameraInfo);
			
			UvcLibrary.uvc_free_device_descriptor(desc.get());
		}
		UvcLibrary.uvc_free_device_list(devList.get(), (byte) 1);
		
		return ids;
	}
	
	public static String getString(Pointer<Byte> p) {
		if (p == null) {
			return null;
		}
		return p.getCString();
	}
	
	@Override
	public void close() throws IOException {
		super.close();
		UvcLibrary.uvc_stop_streaming(devh.get());
		UvcLibrary.uvc_close(devh.get());
		UvcLibrary.uvc_unref_device(dev.get());
		UvcLibrary.uvc_exit(ctx.get());
	}	
	
	public class CameraId {
		final public byte busNumber;
		final public byte devAddress;
		final public int vendorId;
		final public int productId;
		final public String manufacturer;
		final public String product;
		final public String serial;
		
		public CameraId(byte busNumber, byte devAddress, int vendorId, 
				int productId, String manufacturer, 
				String product, String serial) {
			this.busNumber = busNumber;
			this.devAddress = devAddress;
			this.vendorId = vendorId;
			this.productId = productId;
			this.manufacturer = manufacturer;
			this.product = product;
			this.serial = serial;
		}
		
		@Override
		public String toString() {
			String id = "";
			if (manufacturer != null) {
				id += manufacturer;
			}
			if (product != null) {
				if (id.length() > 0) {
					id += " - ";
				}
				id += product;
			}
			if (serial != null) {
				if (id.length() > 0) {
					id += " ";
				}
				id += "(" + serial + ")";
			}
			if (id.length() > 0) {
				id += " ";
			}
			id += String.format("[%04x:%04x @ %d:%d]",
					vendorId & 0xffff, productId & 0xffff, busNumber, devAddress);
			return id;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + busNumber;
			result = prime * result + devAddress;
			result = prime * result + ((manufacturer == null) ? 0 : manufacturer.hashCode());
			result = prime * result + ((product == null) ? 0 : product.hashCode());
			result = prime * result + productId;
			result = prime * result + ((serial == null) ? 0 : serial.hashCode());
			result = prime * result + vendorId;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			CameraId other = (CameraId) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (busNumber != other.busNumber)
				return false;
			if (devAddress != other.devAddress)
				return false;
			if (manufacturer == null) {
				if (other.manufacturer != null)
					return false;
			} else if (!manufacturer.equals(other.manufacturer))
				return false;
			if (product == null) {
				if (other.product != null)
					return false;
			} else if (!product.equals(other.product))
				return false;
			if (productId != other.productId)
				return false;
			if (serial == null) {
				if (other.serial != null)
					return false;
			} else if (!serial.equals(other.serial))
				return false;
			if (vendorId != other.vendorId)
				return false;
			return true;
		}

		private LibuvcCamera getOuterType() {
			return LibuvcCamera.this;
		}
	}
}

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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import javax.swing.Action;

import org.bridj.IntValuedEnum;
import org.bridj.Pointer;
import org.openpnp.CameraListener;
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
import org.simpleframework.xml.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO:
 * when finding the matching camera make sure it's at least a match for the
 * primary three identifiers (vid, pid, serial) or don't consider it at all
 * 
 * Look at uvc_get_input_terminals for discovering capabilities with
 * https://github.com/saki4510t/UVCCamera/blob/master/libuvccamera/src/main/jni/UVCCamera/UVCCamera.cpp#L270
 * 
 * Mark https://github.com/openpnp/openpnp/issues/73 complete.
 */
public class LibuvcCamera extends ReferenceCamera {
	static private Logger logger = LoggerFactory.getLogger(LibuvcCamera.class);
	
	static private Pointer<Pointer<uvc_context>> ctx;
	static {
		IntValuedEnum<uvc_error> err;
		
		ctx = Pointer.allocatePointer(uvc_context.class);
		err = UvcLibrary.uvc_init(ctx, null);
		if (err != uvc_error.UVC_SUCCESS) {
			throw new Error("uvc_init " + err);
		}
	}

	@Element(required=false)
	private CameraId cameraId = null;
	@Attribute(required=false)
	private boolean manualExposureEnabled = false;
	@Attribute(required=false)
	private int manualExposureTime = 0;
	@Attribute(required=false)
	private int resolutionX = 640;
	@Attribute(required=false)
	private int resolutionY = 480;
	@Attribute(required=false)
	private int fps = 30;
	@Attribute(required=false)
	private UvcLibrary.uvc_frame_format frameFormat = UvcLibrary.uvc_frame_format.UVC_FRAME_FORMAT_MJPEG;
	
	private Pointer<uvc_device> dev;
	private Pointer<uvc_device_handle> devh;
	private boolean streaming = false;
	
	private BufferedImage lastFrame;
	
	public LibuvcCamera() {
	}
	
	private synchronized void open(CameraId id) {
		logger.debug("open {}", id);
		if (Objects.equals(this.cameraId, id) && streaming) {
			return;
		}
		
		// first shut down the camera if it's active
		if (streaming) {
			UvcLibrary.uvc_stop_streaming(devh);
			streaming = false;
		}
		if (devh != null) {
			UvcLibrary.uvc_close(devh);
			devh = null;
		}
		if (dev != null) {
			UvcLibrary.uvc_unref_device(dev);
			dev = null;
		}

		if (id == null) {
			return;
		}
		
		IntValuedEnum<uvc_error> err;
		
		// find the best match for the device
		Pointer<uvc_device> dev = findDevice(id);
		if (dev == null) {
			logger.error("No device found for CameraId {}", id);
			return;
		}
		
		// open it
		Pointer<Pointer<uvc_device_handle>> devh = Pointer.allocatePointer(uvc_device_handle.class);
		err = UvcLibrary.uvc_open(dev, devh);
		if (err != uvc_error.UVC_SUCCESS) {
			logger.error("uvc_open {}", err);
			return;
		}
		
		// set up the stream control parameters
		// TODO: configure
		Pointer<uvc_stream_ctrl> ctrl = Pointer.allocate(uvc_stream_ctrl.class);
		err = UvcLibrary.uvc_get_stream_ctrl_format_size(
		          devh.get(), 
		          ctrl, 
		          frameFormat, 
		          resolutionX, 
		          resolutionY, 
		          fps);
		if (err != uvc_error.UVC_SUCCESS) {
			logger.error("uvc_get_stream_ctrl_format_size {}", err);
			return;
		}

		this.dev = dev;
		this.devh = devh.get();
		this.streaming = true;
		
		// start streaming
		err = UvcLibrary.uvc_start_streaming(devh.get(), ctrl, cb.toPointer(), null, (byte) 0);
		if (err != uvc_error.UVC_SUCCESS) {
			logger.error("uvc_start_streaming {}", err);
			return;
		}
		
		setManualExposureEnabled(this.manualExposureEnabled);
		setManualExposureTime(this.manualExposureTime);
	}
	
//	private void readSettings() {
//		IntValuedEnum<uvc_error> err;
//		Pointer<Byte> b = Pointer.allocateByte();
//		Pointer<Short> s = Pointer.allocateShort();
//		Pointer<Integer> i = Pointer.allocateInt();
//		
//		err = UvcLibrary.uvc_get_ae_mode(devh, b, UvcLibrary.uvc_req_code.UVC_GET_DEF);
//		if (err != uvc_error.UVC_SUCCESS) {
//			logger.error("uvc_get_ae_mode {}", err);
//		}
//		else {
//			logger.info("uvc_get_ae_mode UVC_GET_DEF {}", b.getByte());
//		}
//		
//		err = UvcLibrary.uvc_get_ae_mode(devh, b, UvcLibrary.uvc_req_code.UVC_GET_MIN);
//		if (err != uvc_error.UVC_SUCCESS) {
//			logger.error("uvc_get_ae_mode {}", err);
//		}
//		else {
//			logger.info("uvc_get_ae_mode UVC_GET_MIN {}", b.getByte());
//		}
//		
//		err = UvcLibrary.uvc_get_ae_mode(devh, b, UvcLibrary.uvc_req_code.UVC_GET_MAX);
//		if (err != uvc_error.UVC_SUCCESS) {
//			logger.error("uvc_get_ae_mode {}", err);
//		}
//		else {
//			logger.info("uvc_get_ae_mode UVC_GET_MAX {}", b.getByte());
//		}
//		
//		
//		
//		
//		err = UvcLibrary.uvc_get_exposure_abs(devh, i, UvcLibrary.uvc_req_code.UVC_GET_DEF);
//		if (err != uvc_error.UVC_SUCCESS) {
//			logger.error("uvc_get_exposure_abs {}", err);
//		}
//		else {
//			logger.info("uvc_get_exposure_abs UVC_GET_DEF {}", b.getByte());
//		}
//		
//		err = UvcLibrary.uvc_get_exposure_abs(devh, i, UvcLibrary.uvc_req_code.UVC_GET_MIN);
//		if (err != uvc_error.UVC_SUCCESS) {
//			logger.error("uvc_get_exposure_abs {}", err);
//		}
//		else {
//			logger.info("uvc_get_exposure_abs UVC_GET_MIN {}", b.getByte());
//		}
//		
//		err = UvcLibrary.uvc_get_exposure_abs(devh, i, UvcLibrary.uvc_req_code.UVC_GET_MAX);
//		if (err != uvc_error.UVC_SUCCESS) {
//			logger.error("uvc_get_exposure_abs {}", err);
//		}
//		else {
//			logger.info("uvc_get_exposure_abs UVC_GET_MAX {}", b.getByte());
//		}
//		
//		
//		
//		
//		err = UvcLibrary.uvc_get_iris_abs(devh, s, UvcLibrary.uvc_req_code.UVC_GET_DEF);
//		if (err != uvc_error.UVC_SUCCESS) {
//			logger.error("uvc_get_iris_abs {}", err);
//		}
//		else {
//			logger.info("uvc_get_iris_abs UVC_GET_DEF {}", s.getByte());
//		}
//		
//		err = UvcLibrary.uvc_get_iris_abs(devh, s, UvcLibrary.uvc_req_code.UVC_GET_MIN);
//		if (err != uvc_error.UVC_SUCCESS) {
//			logger.error("uvc_get_iris_abs {}", err);
//		}
//		else {
//			logger.info("uvc_get_iris_abs UVC_GET_MIN {}", s.getByte());
//		}
//		
//		err = UvcLibrary.uvc_get_iris_abs(devh, s, UvcLibrary.uvc_req_code.UVC_GET_MAX);
//		if (err != uvc_error.UVC_SUCCESS) {
//			logger.error("uvc_get_iris_abs {}", err);
//		}
//		else {
//			logger.info("uvc_get_iris_abs UVC_GET_MAX {}", s.getByte());
//		}
//		
//		
//		
//		
//		err = UvcLibrary.uvc_get_brightness(devh, s, UvcLibrary.uvc_req_code.UVC_GET_DEF);
//		if (err != uvc_error.UVC_SUCCESS) {
//			logger.error("uvc_get_brightness {}", err);
//		}
//		else {
//			logger.info("uvc_get_brightness UVC_GET_DEF {}", s.getByte());
//		}
//		
//		err = UvcLibrary.uvc_get_brightness(devh, s, UvcLibrary.uvc_req_code.UVC_GET_MIN);
//		if (err != uvc_error.UVC_SUCCESS) {
//			logger.error("uvc_get_brightness {}", err);
//		}
//		else {
//			logger.info("uvc_get_brightness UVC_GET_MIN {}", s.getByte());
//		}
//		
//		err = UvcLibrary.uvc_get_brightness(devh, s, UvcLibrary.uvc_req_code.UVC_GET_MAX);
//		if (err != uvc_error.UVC_SUCCESS) {
//			logger.error("uvc_get_brightness {}", err);
//		}
//		else {
//			logger.info("uvc_get_brightness UVC_GET_MAX {}", s.getByte());
//		}
//		
//		
//		
//		
//		err = UvcLibrary.uvc_get_gain(devh, s, UvcLibrary.uvc_req_code.UVC_GET_DEF);
//		if (err != uvc_error.UVC_SUCCESS) {
//			logger.error("uvc_get_gain {}", err);
//		}
//		else {
//			logger.info("uvc_get_gain UVC_GET_DEF {}", s.getByte());
//		}
//		
//		err = UvcLibrary.uvc_get_gain(devh, s, UvcLibrary.uvc_req_code.UVC_GET_MIN);
//		if (err != uvc_error.UVC_SUCCESS) {
//			logger.error("uvc_get_gain {}", err);
//		}
//		else {
//			logger.info("uvc_get_gain UVC_GET_MIN {}", s.getByte());
//		}
//		
//		err = UvcLibrary.uvc_get_gain(devh, s, UvcLibrary.uvc_req_code.UVC_GET_MAX);
//		if (err != uvc_error.UVC_SUCCESS) {
//			logger.error("uvc_get_gain {}", err);
//		}
//		else {
//			logger.info("uvc_get_gain UVC_GET_MAX {}", s.getByte());
//		}
//	}
	
	@Override
	public synchronized void close() throws IOException {
		super.close();
		open(null);
		// TODO: check if we're the last instance and shut down the context if
		// so.
//		if (ctx != null) {
//			UvcLibrary.uvc_exit(ctx.get());
//		}
	}	
	
	UvcLibrary.uvc_frame_callback_t cb = new UvcLibrary.uvc_frame_callback_t() {
		@Override
		public void apply(Pointer<uvc_frame> frame, Pointer<?> user_ptr) {
			BufferedImage image = decodeFrame(frame);
			if (image != null) {
				image = transformImage(image);
				lastFrame = image;
			}
			broadcastCapture(lastFrame);
		}
	};
	
	private static BufferedImage decodeFrame(Pointer<uvc_frame> frame) {
		boolean mjpeg = false;
		// Have to use .equals here instead of == otherwise we always get
		// UVC_FRAME_FORMAT_ANY. Possibly a generation error, but not sure.
		if (frame.get().frame_format().equals(UvcLibrary.uvc_frame_format.UVC_FRAME_FORMAT_MJPEG)) {
			mjpeg = true;
		}
		
		int width = frame.get().width();
		int height = frame.get().height();
		
		IntValuedEnum<UvcLibrary.uvc_error> err;
		Pointer<uvc_frame> tmpFrame = UvcLibrary.uvc_allocate_frame(width * height * 3);
		if (tmpFrame == null || tmpFrame.get() == null) {
			logger.error("uvc_allocate_frame");
			return null;
		}
		
		if (mjpeg) {
			err = UvcLibrary.uvc_mjpeg2rgb(frame, tmpFrame);
		}
		else {
			err = UvcLibrary.uvc_any2bgr(frame, tmpFrame);
		}
		if (err != UvcLibrary.uvc_error.UVC_SUCCESS) {
			logger.error("{} {}", mjpeg ? "uvc_mjpeg2rgb" : "uvc_any2bgr", err);
			UvcLibrary.uvc_free_frame(tmpFrame);
			return null;
		}

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
		byte[] dst = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
		byte[] src = tmpFrame.get().data().getBytes(width * height * 3);
		if (mjpeg) {
			// copy bytes over swapping red and blue to convert from RGB to BGR
			for (int i = 0, count = width * height * 3; i < count; i += 3) {
				dst[i + 0] = src[i + 2];
				dst[i + 1] = src[i + 1];
				dst[i + 2] = src[i + 0];
			}
		}
		else {
			System.arraycopy(src, 0, dst, 0, src.length);
		}
		
		UvcLibrary.uvc_free_frame(tmpFrame);
		
		return image;
	}
	
	@Override
	public synchronized void startContinuousCapture(CameraListener listener, int maximumFps) {
		open(cameraId);
		super.startContinuousCapture(listener, maximumFps);
	}
	
	@Override
	public synchronized BufferedImage capture() {
		if (!streaming) {
			open(this.cameraId);
		}
		return lastFrame;
	}

	/**
	 * Returns a list of uniquely identifiable camera IDs. 
	 * @return
	 */
	public List<CameraId> getCameraIds(boolean freeDevs) {
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
			
			CameraId id = new CameraId(busNumber, devAddress, 
					vendorId, productId, manufacturer, product, serial);
			id.dev = dev;
			
			ids.add(id);
			
			UvcLibrary.uvc_free_device_descriptor(desc.get());
		}
		UvcLibrary.uvc_free_device_list(devList.get(), freeDevs ? (byte) 1 : 0);
		
		return ids;
	}
	
	public CameraId getCameraId() {
		return cameraId;
	}
	
	public void setCameraId(CameraId cameraId) {
		this.cameraId = cameraId;
		open(cameraId);
	}
	
	public boolean isManualExposureEnabled() {
		return manualExposureEnabled;
	}
	
	public synchronized void setManualExposureEnabled(boolean enabled) {
		if (devh != null) {
			IntValuedEnum<uvc_error> err;
			if (enabled) {
				err = UvcLibrary.uvc_set_ae_mode(devh, (byte) 1);
				if (err != uvc_error.UVC_SUCCESS) {
					logger.error("uvc_set_ae_mode {}", err);
					return;
				}
			}
			else {
				// reset to default
				Pointer<Byte> val = Pointer.allocateByte();
				err = UvcLibrary.uvc_get_ae_mode(devh, val, UvcLibrary.uvc_req_code.UVC_GET_DEF);
				if (err != uvc_error.UVC_SUCCESS) {
					logger.error("uvc_get_ae_mode {}", err);
					return;
				}
				err = UvcLibrary.uvc_set_ae_mode(devh, val.getByte());
				if (err != uvc_error.UVC_SUCCESS) {
					logger.error("uvc_set_ae_mode {}", err);
					return;
				}
			}
		}
		this.manualExposureEnabled = enabled;
	}
	
	public int getManualExposureTime() {
		return manualExposureTime;
	}
	
	/**
	 * Set the manual exposure time in 1/10000ths of a second.
	 * @param val
	 */
	public synchronized void setManualExposureTime(int val) {
		if (devh != null) {
			IntValuedEnum<uvc_error> err;
			err = UvcLibrary.uvc_set_exposure_abs(devh, val);
			if (err != uvc_error.UVC_SUCCESS) {
				logger.error("uvc_set_exposure_abs {}", err);
				return;
			}
		}
		this.manualExposureTime = val;
	}
	
	public int getResolutionX() {
		return resolutionX;
	}

	public void setResolutionX(int resolutionX) {
		this.resolutionX = resolutionX;
	}

	public int getResolutionY() {
		return resolutionY;
	}

	public void setResolutionY(int resolutionY) {
		this.resolutionY = resolutionY;
	}

	public int getFps() {
		return fps;
	}

	public void setFps(int fps) {
		this.fps = fps;
	}

	public UvcLibrary.uvc_frame_format getFrameFormat() {
		return frameFormat;
	}

	public void setFrameFormat(UvcLibrary.uvc_frame_format frameFormat) {
		this.frameFormat = frameFormat;
	}

	private Pointer<uvc_device> findDevice(CameraId id) {
		// sort by least to most specific match and return the first object
		// or null
		List<CameraId> cameraIds = getCameraIds(false);
		if (cameraIds.isEmpty()) {
			return null;
		}
		
		cameraIds.sort(new Comparator<CameraId>() {
			@Override
			public int compare(CameraId o1, CameraId o2) {
				return o2.compareTo(id) - o1.compareTo(id);
			}
		});
		
		CameraId foundId = cameraIds.remove(0);
		logger.debug("Searched for {}, found {}", id, foundId);

		// since we called getCameraIds without freeing the devices we
		// now need to free the ones we aren't using
		for (CameraId tmpId : cameraIds) {
			UvcLibrary.uvc_unref_device(tmpId.dev);
		}
		
		return foundId.dev;
	}
	
	private static String getString(Pointer<Byte> p) {
		if (p == null) {
			return null;
		}
		return p.getCString();
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
	
	public static class CameraId implements Comparable<CameraId> {
		@Attribute(required=true)
		private byte busNumber;

		@Attribute(required=true)
		private byte devAddress;
		
		@Attribute(required=true)
		private int vendorId;
		
		@Attribute(required=true)
		private int productId;
		
		@Attribute(required=false)
		private String manufacturer;
		
		@Attribute(required=false)
		private String product;
		
		@Attribute(required=false)
		private String serial;
		
		public volatile Pointer<uvc_device> dev;
		
		public CameraId() {
			
		}
		
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
		
		public byte getBusNumber() {
			return busNumber;
		}

		public byte getDevAddress() {
			return devAddress;
		}

		public int getVendorId() {
			return vendorId;
		}

		public int getProductId() {
			return productId;
		}

		public String getManufacturer() {
			return manufacturer == null ? "" : manufacturer;
		}

		public String getProduct() {
			return product == null ? "" : product;
		}

		public String getSerial() {
			return serial == null ? "" : serial;
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

		public int compareTo(CameraId id) {
			if (this.vendorId != id.vendorId) {
				return -5;
			}
			if (this.productId != id.productId) {
				return -4;
			}
			if (!Objects.equals(this.serial, id.serial)) {
				return -3;
			}
			if (this.busNumber != id.busNumber) {
				return -2;
			}
			if (this.devAddress != id.devAddress) {
				return -1;
			}
			return 0;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
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
		
	}
}

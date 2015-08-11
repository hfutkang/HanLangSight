package com.ingenic.glass.camera;

/**
 * /etc/takepicture_profiles.xml Element
 * @author Added by dybai_bj 20150625
 *
 */
public class TakePictureProfile {

	private String cameraId;

	private String quality;

	private String fileFormat;

	private String width;

	private String height;

	public String getCameraId() {
		return cameraId;
	}

	public void setCameraId(String cameraId) {
		this.cameraId = cameraId;
	}

	public String getQuality() {
		return quality;
	}

	public void setQuality(String quality) {
		this.quality = quality;
	}

	public String getFileFormat() {
		return fileFormat;
	}

	public void setFileFormat(String fileFormat) {
		this.fileFormat = fileFormat;
	}

	public String getWidth() {
		return width;
	}

	public void setWidth(String width) {
		this.width = width;
	}

	public String getHeight() {
		return height;
	}

	public void setHeight(String height) {
		this.height = height;
	}
}

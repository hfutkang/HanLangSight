package com.ingenic.glass.camera;

import java.util.ArrayList;
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Analytical takepicture_profiles.xml file
 * @author Added by dybai_bj 20150625
 *
 */
public class TakePictureSAX extends DefaultHandler {

	private List<TakePictureProfile> takePicturePorfileList;
	private String cameraId = null;

	public TakePictureSAX () {
		this.takePicturePorfileList = new ArrayList<TakePictureProfile>();
	}

	/**
	 * Start analytical xml element
	 * @author Added by dybai_bj 20150625
	 */
	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes attributes) throws SAXException {
		if ("CamcorderProfiles".equals(localName)) {
			this.cameraId = attributes.getValue("cameraId");
		} else if ("EncoderProfile".equals(localName)) {
			TakePictureProfile takePictureProfile = new TakePictureProfile();
			takePictureProfile.setCameraId(this.cameraId);
			takePictureProfile.setQuality(attributes.getValue("quality"));
			takePictureProfile.setFileFormat(attributes.getValue("fileFormat"));
			takePictureProfile.setWidth(attributes.getValue("width"));
			takePictureProfile.setHeight(attributes.getValue("height"));
			this.takePicturePorfileList.add(takePictureProfile);
		}
	}

	/**
	 * Get analytical xml result
	 * @return A &lt;EncoderProfile&gt; node corresponds to
	 * a takePicturePorfileList member
	 * @author Added by dybai_bj 20150625
	 */
	public List<TakePictureProfile> getTakePicturePorfileList() {
		return takePicturePorfileList;
	}

}

package com.ctrip.esdemo.utils;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.util.StreamReaderDelegate;

public class XmlUtil {
	static HashMap<Character, String> h = new HashMap<Character, String>();
	static{
		h.put('&', "&amp;");
		h.put('"', "&quot;");
		h.put('<', "&lt;");
		h.put('>', "&gt;");
		h.put('\'', "&apos;");
	}
	
	public static String escape(String str){
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			if(h.containsKey(c)){
				sb.append(h.get(c));
			}else{
				sb.append(c);
			}
		}
		return sb.toString();
	}
	
	// Export
	@SuppressWarnings("unchecked")
	public static <T> String marshal(T object) throws IOException, JAXBException{
		Class<T> clzz = (Class<T>) object.getClass();
		JAXBContext context;
		context = JAXBContext.newInstance(clzz);
		Marshaller m = context.createMarshaller();
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
		m.marshal(object, os);
		return os.toString();
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T unmarshal(String xml, Class<?> clzz) throws JAXBException, UnsupportedEncodingException{
		JAXBContext context = JAXBContext.newInstance(clzz);
		Unmarshaller um = context.createUnmarshaller();
		ByteArrayInputStream is = new ByteArrayInputStream(xml.getBytes("utf-8"));
		return (T) um.unmarshal(is);
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T unmarshal(String xml, Class<?> clzz, Boolean upperCase) throws JAXBException, UnsupportedEncodingException, XMLStreamException{
		JAXBContext context = JAXBContext.newInstance(clzz);
		Unmarshaller um = context.createUnmarshaller();
		XMLInputFactory xif = XMLInputFactory.newInstance();
		ByteArrayInputStream is = new ByteArrayInputStream(xml.getBytes("utf-8"));
	    XMLStreamReader xsr = xif.createXMLStreamReader(is);
	    xsr = new UpperValueStreamReaderDelegate(xsr, upperCase);
		return (T) um.unmarshal(xsr);
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T unmarshal(InputStream in, Class<?> clzz) throws JAXBException, UnsupportedEncodingException{
		JAXBContext context = JAXBContext.newInstance(clzz);
		Unmarshaller um = context.createUnmarshaller();
		return (T) um.unmarshal(in);
	}
	
	public static class UpperValueStreamReaderDelegate extends StreamReaderDelegate {
		private Boolean upperCase;
		
		public UpperValueStreamReaderDelegate(XMLStreamReader xsr, Boolean upperCase) {
			super(xsr);
			this.upperCase = upperCase;
		}

		@Override
		public String getElementText() throws XMLStreamException{
			if (upperCase){
				return super.getElementText().toUpperCase();
			}else {
				return super.getElementText().toLowerCase();
			}
		}

		@Override
		public String getAttributeValue(int index) {
			if (upperCase){
				return super.getAttributeValue(index).toUpperCase();
			}else {
				return super.getAttributeValue(index).toLowerCase();
			}
			
		}

		@Override
		public String getText() {
			if (upperCase){
				return super.getText().toUpperCase();
			}else {
				return super.getText().toLowerCase();
			}
		}

	}
}

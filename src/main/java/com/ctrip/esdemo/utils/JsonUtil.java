package com.ctrip.esdemo.utils;


import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

public class JsonUtil {

	private static final ObjectMapper objectMapper = new ObjectMapper();
	
	static {
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	public static <T> List<T> parserJsonList(InputStream instream, Class<T> clsT) {
		try {
			ObjectReader reader = objectMapper.reader(clsT);
			MappingIterator<T> iterator = reader.readValues(instream);
			List<T> list = new LinkedList<T>();
			while (iterator.hasNext()) {
				list.add(iterator.next());
			}
			return list;
		} catch (JsonParseException e) {
			throw new RuntimeException("parse json error", e);
		} catch (IOException e) {
			throw new RuntimeException("parse json error", e);
		} finally {
			try {
				instream.close();
			} catch (Exception ignore) {
			}
		}
	}
	
	public static <T> List<T> parserJsonList(String str, Class<T> clsT) {
		try {
			ObjectReader reader = objectMapper.reader(clsT);
			MappingIterator<T> iterator = reader.readValues(str);
			List<T> list = new LinkedList<T>();
			while (iterator.hasNext()) {
				list.add(iterator.next());
			}
			return list;
		} catch (JsonParseException e) {
			throw new RuntimeException("parse json error str:" + str, e);
		} catch (IOException e) {
			throw new RuntimeException("parse json error str:" + str, e);
		} 
	}
	
	public static <T> LinkedHashMap<String, T> parserJsonMap(String str, Class<T> clsT) {
		LinkedHashMap<String, T> map = new LinkedHashMap<String, T>();
		try {
			JsonParser parser = objectMapper.getFactory().createParser(str);
			JsonToken current;
			current = parser.nextToken();
			if (current != JsonToken.START_OBJECT) {
				throw new RuntimeException("parse json error: root should be object, quiting.");
			}

			while (parser.nextToken() != JsonToken.END_OBJECT) {
				String fieldName = parser.getCurrentName();
				current = parser.nextToken();
				T obj = parser.readValueAs(clsT);
				map.put(fieldName, obj);		
			}
			
			return map;
		} catch (JsonParseException e) {
			throw new RuntimeException("parse json error str:" + str, e);
		} catch (IOException e) {
			throw new RuntimeException("parse json error str:" + str, e);
		} 
	}
	
	public static <T extends Enum<T>> EnumSet<T> parserJsonEnum(String str, Class<T> clsT) {
		try {
			JsonParser parser = objectMapper.getFactory().createParser(str);
			parser.nextToken();
			EnumSet<T> enumSet = EnumSet.noneOf(clsT);
			while (parser.nextToken() == JsonToken.START_OBJECT) {
				enumSet.add(objectMapper.readValue(parser, clsT));
			}
			return enumSet;
		} catch (JsonParseException e) {
			throw new RuntimeException("parse json error str:" + str, e);
		} catch (IOException e) {
			throw new RuntimeException("parse json error str:" + str, e);
		} 
	}

	public static <T> T parserJson(InputStream instream, Class<T> cls) {
		try {
			JsonParser parser = objectMapper.getFactory().createParser(instream);
			T t = objectMapper.readValue(parser, cls);
			return t;
		} catch (JsonParseException e) {
			throw new RuntimeException("parse json error", e);
		} catch (IOException e) {
			throw new RuntimeException("parse json error", e);
		} finally {
			try {
				instream.close();
			} catch (Exception ignore) {

			}
		}
	}

	public static <T> T parserJson(String str, Class<T> cls) {
		try {
			JsonParser parser = objectMapper.getFactory().createParser(str);
			T t = objectMapper.readValue(parser, cls);
			return t;
		} catch (JsonParseException e) {
			throw new RuntimeException("parse json error, str:" + str, e);
		} catch (IOException e) {
			throw new RuntimeException("parse json error, str:" + str, e);
		}
	}
	
	public static String getJsonFromObject(Object object) {
		try {
			return objectMapper.writeValueAsString(object);
		} catch (JsonGenerationException e) {
			throw new RuntimeException("get json error", e);
		} catch (JsonMappingException e) {
			throw new RuntimeException("get json error", e);
		} catch (IOException e) {
			throw new RuntimeException("get json error", e);
		}
	}
}
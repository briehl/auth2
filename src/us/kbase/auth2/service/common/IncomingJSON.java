package us.kbase.auth2.service.common;

import static us.kbase.auth2.lib.Utils.checkString;

import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.google.common.base.Optional;

import us.kbase.auth2.lib.exceptions.IllegalParameterException;
import us.kbase.auth2.lib.exceptions.MissingParameterException;

public class IncomingJSON {

	// TODO JAVADOC
	// TODO TEST

	private final Map<String, Object> additionalProperties = new TreeMap<>();

	// don't throw error from constructor, doesn't get picked up by the custom error handler 
	protected IncomingJSON() {}
	
	public static String getString(final String string, final String field)
			throws MissingParameterException {
		checkString(string, field);
		return string.trim();
	}

	protected Optional<String> getOptionalString(final String string) {
		if (string == null || string.trim().isEmpty()) {
			return Optional.absent();
		}
		return Optional.of(string);
	}

	protected boolean getBoolean(final Object b, final String fieldName)
			throws IllegalParameterException {
		if (b == null) {
			return false;
		}
		if (!(b instanceof Boolean)) {
			throw new IllegalParameterException(fieldName + " must be a boolean");
		}
		// may need to configure response for null
		return Boolean.TRUE.equals(b) ? true : false;
	}

	@JsonAnyGetter
	public Map<String, Object> getAdditionalProperties() {
		return this.additionalProperties;
	}

	@JsonAnySetter
	public void setAdditionalProperties(String name, Object value) {
		this.additionalProperties.put(name, value);
	}

	public void exceptOnAdditionalProperties() throws IllegalParameterException {
		if (!additionalProperties.isEmpty()) {
			throw new IllegalParameterException("Unexpected parameters in request: " + 
					String.join(", ", additionalProperties.keySet()));
		}
	}

}

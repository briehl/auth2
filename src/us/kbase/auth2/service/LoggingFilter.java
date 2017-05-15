package us.kbase.auth2.service;

import java.io.IOException;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Context;

import org.slf4j.LoggerFactory;

import us.kbase.auth2.lib.Authentication;
import us.kbase.auth2.lib.config.ConfigAction.State;
import us.kbase.auth2.lib.exceptions.ExternalConfigMappingException;
import us.kbase.auth2.lib.storage.exceptions.AuthStorageException;
import us.kbase.auth2.service.AuthExternalConfig.AuthExternalConfigMapper;

/** The logger for the auth service. Sets up the logging info (e.g. the method, a random call ID,
 * and the IP address) for each request and logs the method, path, status code, and user agent on
 * a response.
 * @author gaprice@lbl.gov
 *
 */
public class LoggingFilter implements ContainerRequestFilter,
		ContainerResponseFilter {
	
	//TODO TEST unit tests
	
	private static final String X_FORWARDED_FOR = "X-Forwarded-For";
	private static final String X_REAL_IP = "X-Real-IP";
	private static final String USER_AGENT = "User-Agent";
	
	@Context
	private HttpServletRequest servletRequest;
	
	@Inject
	private SLF4JAutoLogger logger;
	@Inject
	private Authentication auth;
	
	@Override
	public void filter(final ContainerRequestContext reqcon)
			throws IOException {
		boolean ignoreIPheaders = true;
		try {
			final AuthExternalConfig<State> ext = auth.getExternalConfig(
					new AuthExternalConfigMapper());
			ignoreIPheaders = ext.isIgnoreIPHeadersOrDefault();
		} catch (AuthStorageException | ExternalConfigMappingException e) {
			LoggerFactory.getLogger(getClass()).error(
					"An error occurred in the logger when attempting " +
					"to get the server configuration", e); 
		}
		logger.setCallInfo(reqcon.getMethod(),
				(String.format("%.16f", Math.random())).substring(2),
				getIpAddress(reqcon, ignoreIPheaders));
	}
	
	//TODO TEST xff and realip headers
	private String getIpAddress(
			final ContainerRequestContext request,
			final boolean ignoreIPsInHeaders) {
		final String xFF = request.getHeaderString(X_FORWARDED_FOR);
		final String realIP = request.getHeaderString(X_REAL_IP);

		if (!ignoreIPsInHeaders) {
			if (xFF != null && !xFF.trim().isEmpty()) {
				return xFF.split(",")[0].trim();
			}
			if (realIP != null && !realIP.trim().isEmpty()) {
				return realIP.trim();
			}
		}
		//TODO LOG always log xff, real ip, and request ip
		return servletRequest.getRemoteAddr();
	}

	@Override
	public void filter(
			final ContainerRequestContext reqcon,
			final ContainerResponseContext rescon)
			throws IOException {
		LoggerFactory.getLogger(getClass()).info("{} {} {} {}",
				reqcon.getMethod(),
				reqcon.getUriInfo().getAbsolutePath(),
				rescon.getStatus(),
				reqcon.getHeaderString(USER_AGENT));
	}

}

package us.kbase.auth2.service.api;

import static us.kbase.auth2.service.api.APIUtils.relativize;
import static us.kbase.auth2.service.api.APIUtils.getLoginCookie;
import static us.kbase.auth2.service.api.APIUtils.getMaxCookieAge;
import static us.kbase.auth2.service.api.APIUtils.upperCase;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.NoSuchProviderException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

import javax.inject.Inject;
import javax.ws.rs.CookieParam;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriInfo;

import org.glassfish.jersey.server.mvc.Template;
import org.glassfish.jersey.server.mvc.Viewable;

import us.kbase.auth2.lib.AuthUser;
import us.kbase.auth2.lib.Authentication;
import us.kbase.auth2.lib.LoginToken;
import us.kbase.auth2.lib.UserName;
import us.kbase.auth2.lib.exceptions.AuthenticationException;
import us.kbase.auth2.lib.exceptions.ErrorType;
import us.kbase.auth2.lib.exceptions.ExternalConfigMappingException;
import us.kbase.auth2.lib.exceptions.IllegalParameterException;
import us.kbase.auth2.lib.exceptions.InvalidTokenException;
import us.kbase.auth2.lib.exceptions.MissingParameterException;
import us.kbase.auth2.lib.exceptions.NoSuchIdentityProviderException;
import us.kbase.auth2.lib.exceptions.NoTokenProvidedException;
import us.kbase.auth2.lib.exceptions.UnauthorizedException;
import us.kbase.auth2.lib.exceptions.UserExistsException;
import us.kbase.auth2.lib.identity.RemoteIdentityWithID;
import us.kbase.auth2.lib.storage.exceptions.AuthStorageException;
import us.kbase.auth2.lib.token.IncomingToken;
import us.kbase.auth2.lib.token.NewToken;
import us.kbase.auth2.lib.token.TemporaryToken;
import us.kbase.auth2.service.AuthAPIStaticConfig;
import us.kbase.auth2.service.AuthExternalConfig;
import us.kbase.auth2.service.AuthExternalConfig.AuthExternalConfigMapper;

@Path("/login")
public class Login {

	//TODO TEST
	//TODO JAVADOC
	
	//TODO CODE rename service.api -> service.ui. service.api will be for the actual API code, including legacy stuff

	@Inject
	private Authentication auth;
	
	@Inject
	private AuthAPIStaticConfig cfg;
	
	@GET
	public Response loginStart(
			@QueryParam("provider") final String provider,
			@QueryParam("redirect") final String redirect,
			@Context UriInfo uriInfo)
			throws NoSuchIdentityProviderException, AuthStorageException,
			IllegalParameterException {
		checkRedirectURL(redirect);
		if (provider != null && !provider.trim().isEmpty()) {
			final String state = auth.getBareToken();
			final URI target = toURI(auth.getIdentityProviderURL(provider, state, false));
			
			final ResponseBuilder r = Response.seeOther(target).cookie(getStateCookie(state));
			if (redirect != null && !redirect.trim().isEmpty()) {
					r.cookie(getRedirectCookie(redirect));
			}
			return r.build();
		} else {
			final Map<String, Object> ret = new HashMap<>();
			final List<Map<String, String>> provs = new LinkedList<>();
			ret.put("providers", provs);
			for (final String prov: auth.getIdentityProviders()) {
				final Map<String, String> rep = new HashMap<>();
				rep.put("name", prov);
				final URI i = auth.getIdentityProviderImageURI(prov);
				if (i.isAbsolute()) {
					rep.put("img", i.toString());
				} else {
					rep.put("img", relativize(uriInfo, i));
				}
				provs.add(rep);
			}
			ret.put("hasprov", !provs.isEmpty());
			ret.put("urlpre", "?provider=");
			if (redirect != null && !redirect.trim().isEmpty()) {
				ret.put("redirect", redirect);
			}
			return Response.ok().entity(new Viewable("/loginstart", ret)).build();
		}
	}

	private void checkRedirectURL(final String redirect)
			throws AuthStorageException, IllegalParameterException {
		if (redirect != null && !redirect.trim().isEmpty()) {
			final AuthExternalConfig ext;
			try {
				ext = auth.getExternalConfig(new AuthExternalConfigMapper());
			} catch (ExternalConfigMappingException e) {
				throw new RuntimeException("Dude, like, what just happened?", e);
			}
			try {
				new URL(redirect);
			} catch (MalformedURLException e) {
				throw new IllegalParameterException("Illegal redirect URL: " + redirect);
			}
			if (ext.getAllowedRedirectPrefix() != null) {
				if (!redirect.startsWith(ext.getAllowedRedirectPrefix().toString())) {
					throw new IllegalParameterException(
							"Illegal redirect url: " + redirect);
				}
			}
		}
	}

	private NewCookie getRedirectCookie(final String redirect) {
		return new NewCookie(new Cookie(
				"redirect", redirect == null ? "no redirect" : redirect, "/login", null),
				"redirect url",
				redirect == null ? 0 : 30 * 60, APIConstants.SECURE_COOKIES);
	}

	private NewCookie getStateCookie(final String state) {
		return new NewCookie(new Cookie(
				"statevar", state == null ? "no state" : state, "/login/complete", null),
				"loginstate",
				state == null ? 0 : 30 * 60, APIConstants.SECURE_COOKIES);
	}
	
	@GET
	@Path("/complete/{provider}")
	public Response login(
			@PathParam("provider") String provider,
			@CookieParam("statevar") final String state,
			@CookieParam("redirect") final String redirect,
			@Context final UriInfo uriInfo)
			throws MissingParameterException, AuthenticationException,
			NoSuchProviderException, AuthStorageException,
			UnauthorizedException {
		//TODO INPUT handle error in params (provider, state)
		provider = upperCase(provider);
		final MultivaluedMap<String, String> qps = uriInfo.getQueryParameters();
		//TODO ERRHANDLE handle returned OAuth error code in queryparams
		final String authcode = qps.getFirst("code"); //may need to be configurable
		final String retstate = qps.getFirst("state"); //may need to be configurable
		if (state == null || state.trim().isEmpty()) {
			throw new MissingParameterException("Couldn't retrieve state value from cookie");
		}
		if (!state.equals(retstate)) {
			throw new AuthenticationException(ErrorType.AUTHENTICATION_FAILED,
					"State values do not match, this may be a CXRF attack");
		}
		final LoginToken lr = auth.login(provider, authcode);
		final Response r;
		// always redirect so the authcode doesn't remain in the title bar
		// note nginx will rewrite the redirect appropriately so absolute
		// redirects are ok
		if (lr.isLoggedIn()) {
			r = Response.seeOther(getRedirectURI(redirect, "/me"))
			//TODO LOGIN get keep me logged in from cookie set at start of login
					.cookie(getLoginCookie(cfg.getTokenCookieName(), lr.getToken(), true))
					.cookie(getStateCookie(null))
					.cookie(getRedirectCookie(null)).build();
		} else {
			//TODO LOGIN make target configurable
			r = Response.seeOther(toURI("/login/choice"))
					.cookie(getLoginInProcessCookie(lr.getTemporaryToken()))
					.cookie(getStateCookie(null))
					.build();
		}
		return r;
	}
	
	private URI getRedirectURI(final String redirect, final String deflt) {
		//TODO REDIRECT check redirect url matches allowed config & is valid URL
		if (redirect != null && !redirect.trim().isEmpty()) {
			return toURI(redirect);
		}
		return toURI(deflt);
	}

	private NewCookie getLoginInProcessCookie(final TemporaryToken token) {
		return new NewCookie(new Cookie("in-process-login-token",
				token == null ? "no token" : token.getToken(), "/login", null),
				"logintoken",
				token == null ? 0 : getMaxCookieAge(token, false),
				APIConstants.SECURE_COOKIES);
	}

	@GET
	@Path("/choice")
	@Template(name = "/loginchoice")
	@Produces(MediaType.TEXT_HTML)
	public Map<String, Object> loginChoiceHTML(
			@CookieParam("in-process-login-token") final String token,
			@Context final UriInfo uriInfo)
			throws NoTokenProvidedException, AuthStorageException,
			InvalidTokenException {
		return loginChoice(token, uriInfo);
	}

	// trying to combine JSON and HTML doesn't work - @Template = always HTML regardless of Accept:
	@GET
	@Path("/choice")
	@Produces(MediaType.APPLICATION_JSON)
	public Map<String, Object> loginChoiceJSON(
			@CookieParam("in-process-login-token") final String token,
			@Context final UriInfo uriInfo)
			throws NoTokenProvidedException, AuthStorageException,
			InvalidTokenException {
		return loginChoice(token, uriInfo);
	}
	
	private Map<String, Object> loginChoice(final String token, final UriInfo uriInfo)
			throws NoTokenProvidedException, AuthStorageException, InvalidTokenException {
		if (token == null || token.trim().isEmpty()) {
			throw new NoTokenProvidedException("Missing in-process-login-token");
		}
		final Map<RemoteIdentityWithID, AuthUser> ids = auth.getLoginState(
				new IncomingToken(token.trim()));
		
		final Map<String, Object> ret = new HashMap<>();
		ret.put("createurl", relativize(uriInfo, "/login/create"));
		ret.put("pickurl", relativize(uriInfo, "/login/pick"));
		ret.put("provider", ids.keySet().iterator().next().getRemoteID().getProvider());
		
		final List<Map<String, String>> create = new LinkedList<>();
		final List<Map<String, String>> login = new LinkedList<>();
		ret.put("create", create);
		ret.put("login", login);
		
		for (final Entry<RemoteIdentityWithID, AuthUser> e: ids.entrySet()) {
			final RemoteIdentityWithID id = e.getKey();
			if (e.getValue() == null) {
				final Map<String, String> c = new HashMap<>();
				c.put("id", id.getID().toString());
				//TODO UI get safe username from db. Splitting on @ is not necessarily safe, only do it if it's there
				c.put("usernamesugg", id.getDetails().getUsername().split("@")[0]);
				c.put("prov_username", id.getDetails().getUsername());
				c.put("prov_fullname", id.getDetails().getFullname());
				c.put("prov_email", id.getDetails().getEmail());
				create.add(c);
			} else {
				final Map<String, String> l = new HashMap<>();
				l.put("id", id.getID().toString());
				l.put("prov_username", id.getDetails().getUsername());
				l.put("username", e.getValue().getUserName().getName());
				login.add(l);
			}
		}
		return ret;
	}
	
	// may need another POST endpoint for AJAX with query params and no redirect
	@POST
	@Path("/pick")
	public Response pickAccount(
			@CookieParam("in-process-login-token") final String token,
			@CookieParam("redirect") final String redirect,
			@FormParam("id") final UUID identityID)
			throws NoTokenProvidedException, AuthenticationException,
			AuthStorageException, UnauthorizedException {
		
		if (token == null || token.trim().isEmpty()) {
			throw new NoTokenProvidedException("Missing in-process-login-token");
		}
		final NewToken newtoken = auth.login(new IncomingToken(token), identityID);
		return Response.seeOther(getRedirectURI(redirect, "/me"))
				//TODO LOGIN get keep me logged in from cookie set at start of login
				.cookie(getLoginCookie(cfg.getTokenCookieName(), newtoken, true))
				.cookie(getLoginInProcessCookie(null))
				.cookie(getRedirectCookie(null)).build();
	}
	
	// may need another POST endpoint for AJAX with query params and no redirect
	@POST
	@Path("/create")
	public Response createUser(
			@CookieParam("in-process-login-token") final String token,
			@CookieParam("redirect") final String redirect,
			@FormParam("id") final UUID identityID,
			@FormParam("user") final String userName,
			@FormParam("full") final String fullName,
			@FormParam("email") final String email,
			@FormParam("stayLoggedIn") final String stayLoggedIn,
			@FormParam("private") final String nameAndEmailPrivate)
			throws AuthenticationException, AuthStorageException,
				UserExistsException, NoTokenProvidedException,
				MissingParameterException, IllegalParameterException,
				UnauthorizedException {
		if (token == null || token.trim().isEmpty()) {
			throw new NoTokenProvidedException("Missing in-process-login-token");
		}
		//TODO INPUT sanity check inputs
		final boolean sessionLogin = stayLoggedIn == null || stayLoggedIn.isEmpty();
		final boolean priv = nameAndEmailPrivate != null && nameAndEmailPrivate.isEmpty();
		
		// might want to enapsulate the user data in a NewUser class
		final NewToken newtoken = auth.createUser(new IncomingToken(token),
				identityID, new UserName(userName), fullName, email, sessionLogin, priv);
		return Response.seeOther(getRedirectURI(redirect, "/me"))
				//TODO LOGIN get keep me logged in from cookie set at start of login
				.cookie(getLoginCookie(cfg.getTokenCookieName(), newtoken, true))
				.cookie(getLoginInProcessCookie(null))
				.cookie(getRedirectCookie(null)).build();
	}
	
	//Assumes valid URI in URL form
	private URI toURI(final URL loginURL) {
		try {
			return loginURL.toURI();
		} catch (URISyntaxException e) {
			throw new RuntimeException("This should be impossible", e);
		}
	}
	
	//Assumes valid URI in String form
	private URI toURI(final String uri) {
		try {
			return new URI(uri);
		} catch (URISyntaxException e) {
			throw new RuntimeException("This should be impossible", e);
		}
	}
}

package us.kbase.auth2.lib;

import static us.kbase.auth2.lib.Utils.nonNull;

import com.google.common.base.Optional;

import us.kbase.auth2.lib.token.NewToken;

/** Represents the result of a successful local login, which can result of one of two states.
 * 
 * 1) The login is complete and a new token returned.
 * 2) A password reset is required.
 * 
 * @author gaprice@lbl.gov
 *
 */
public class LocalLoginResult {
	
	private final Optional<UserName> userName;
	private final Optional<NewToken> token;
	
	/** Create a login result where a password reset is required.
	 * @param userName the username of the user that logged in.
	 */
	public LocalLoginResult(final UserName userName) {
		nonNull(userName, "userName");
		this.userName = Optional.of(userName);
		token = Optional.absent();
	}
	
	/** Create a login result where the login is complete.
	 * @param token the user's new token.
	 */
	public LocalLoginResult(final NewToken token) {
		nonNull(token, "token");
		userName = Optional.absent();
		this.token = Optional.of(token);
	}

	/** Returns whether a password reset is required.
	 * @return true if a password reset is required, false otherwise.
	 */
	public boolean isPwdResetRequired() {
		return userName.isPresent();
	}

	/** Get the user's new token.
	 * @return the token, or absent if a password reset is required.
	 */
	public Optional<NewToken> getToken() {
		return token;
	}
	
	/** Get the name of the user requiring a password reset.
	 * @return the username, or absent if a password reset is not required.
	 */
	public Optional<UserName> getUserName() {
		return userName;
	}
}

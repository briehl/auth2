package us.kbase.test.auth2.lib;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static us.kbase.test.auth2.TestCommon.assertClear;
import static us.kbase.test.auth2.TestCommon.set;
import static us.kbase.test.auth2.lib.AuthenticationTester.initTestAuth;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;

import org.junit.Test;

import us.kbase.auth2.cryptutils.RandomDataGenerator;
import us.kbase.auth2.lib.AuthConfig;
import us.kbase.auth2.lib.AuthConfigSet;
import us.kbase.auth2.lib.Authentication;
import us.kbase.auth2.lib.CollectingExternalConfig;
import us.kbase.auth2.lib.CollectingExternalConfig.CollectingExternalConfigMapper;
import us.kbase.auth2.lib.DisplayName;
import us.kbase.auth2.lib.EmailAddress;
import us.kbase.auth2.lib.LocalLoginResult;
import us.kbase.auth2.lib.LocalUser;
import us.kbase.auth2.lib.Password;
import us.kbase.auth2.lib.UUIDGenerator;
import us.kbase.auth2.lib.UserDisabledState;
import us.kbase.auth2.lib.UserName;
import us.kbase.auth2.lib.storage.AuthStorage;
import us.kbase.auth2.lib.token.HashedToken;
import us.kbase.auth2.lib.token.NewToken;
import us.kbase.auth2.lib.token.TokenType;
import us.kbase.test.auth2.lib.AuthenticationTester.TestAuth;

public class AuthenticationPasswordLoginTest {
	
	/* tests anything to do with passwords, including login. */

	@Test
	public void login() throws Exception {
		final TestAuth testauth = initTestAuth();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		final RandomDataGenerator rand = testauth.randGen;
		final Clock clock = testauth.clock;
		final UUIDGenerator uuidGen = testauth.uuid;
		
		AuthenticationTester.setConfigUpdateInterval(auth, 0);
		
		final UserName u = new UserName("foo");
		final Password p = new Password("foobarbazbat".toCharArray());
		final byte[] salt = new byte[] {1, 2, 3, 4, 5, 6, 7, 8};
		final byte[] hash = AuthenticationTester.fromBase64(
				"M0D2KmSM5CoOHojYgbbKQy1UrkLskxrQnWxcaRf3/hs=");
		final UUID id = UUID.randomUUID();
		
		final NewToken expectedToken = new NewToken(id, TokenType.LOGIN, "this is a token",
				new UserName("foo"), Instant.ofEpochMilli(4000), 14 * 24 * 3600 * 1000);
		
		when(storage.getLocalUser(new UserName("foo"))).thenReturn(new LocalUser(
				new UserName("foo"), new EmailAddress("f@g.com"), new DisplayName("foo"),
				Collections.emptySet(), Collections.emptySet(),
				Instant.now(), null, new UserDisabledState(), hash, salt, false, null));
		
		when(storage.getConfig(isA(CollectingExternalConfigMapper.class))).thenReturn(
				new AuthConfigSet<>(new AuthConfig(true, null, null),
						new CollectingExternalConfig(new HashMap<>())));
		
		when(uuidGen.randomUUID()).thenReturn(UUID.fromString(id.toString()), (UUID) null);
		
		when(rand.getToken()).thenReturn("this is a token");
		
		when(clock.instant()).thenReturn(Instant.ofEpochMilli(4000), Instant.ofEpochMilli(6000),
				null);
		
		final LocalLoginResult t = auth.localLogin(u, p);
		
		verify(storage).storeToken(new HashedToken(TokenType.LOGIN, null, t.getToken().getId(),
				"p40z9I2zpElkQqSkhbW6KG3jSgMRFr3ummqjSe7OzOc=", new UserName("foo"),
				Instant.ofEpochMilli(4000), Instant.ofEpochMilli(4000 + 14 * 24 * 3600 * 1000)));
		
		verify(storage).setLastLogin(new UserName("foo"), Instant.ofEpochMilli(6000));
		
		assertClear(p);
		assertThat("incorrect pwd required", t.isPwdResetRequired(), is(false));
		assertThat("incorrect username", t.getUserName(), is((UserName) null));
		assertThat("incorrect token", t.getToken(), is(expectedToken));
	}
	
	
}

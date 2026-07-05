package cc.blynk.server.core.dao;

import cc.blynk.server.core.model.auth.User;
import cc.blynk.utils.AppNameUtil;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the opt-in appName alias fallback in UserDao.
 * With no aliases configured behavior must be identical to legacy:
 * accounts are strictly namespaced by (email, appName).
 */
public class UserDaoAppNameAliasTest {

    private static final String EMAIL = "test@example.com";

    private static UserDao buildDao(Map<String, String> aliases) {
        ConcurrentMap<UserKey, User> users = new ConcurrentHashMap<>();
        User blynkUser = new User(EMAIL, "hash", AppNameUtil.BLYNK, "local", "127.0.0.1", false, false);
        users.put(new UserKey(blynkUser), blynkUser);
        return new UserDao(users, "local", "127.0.0.1", aliases);
    }

    @Test
    public void noAliasMeansLegacyBehavior() {
        UserDao userDao = buildDao(Map.of());

        assertNull(userDao.getByName(EMAIL, "Plynx"));
        assertFalse(userDao.contains(EMAIL, "Plynx"));
        assertFalse(userDao.isUserExists(EMAIL, "Plynx"));

        //exact namespace still works
        assertTrue(userDao.contains(EMAIL, AppNameUtil.BLYNK));
        assertTrue(userDao.isUserExists(EMAIL, AppNameUtil.BLYNK));
    }

    @Test
    public void aliasFallsBackWhenExactMatchIsMissing() {
        UserDao userDao = buildDao(Map.of("Plynx", AppNameUtil.BLYNK));

        User user = userDao.getByName(EMAIL, "Plynx");
        assertSame(userDao.getByName(EMAIL, AppNameUtil.BLYNK), user);
        //user is never mutated, it keeps its original namespace
        assertSame(AppNameUtil.BLYNK, user.appName);

        assertTrue(userDao.contains(EMAIL, "Plynx"));
        assertTrue(userDao.isUserExists(EMAIL, "Plynx"));
    }

    @Test
    public void exactMatchWinsOverAlias() {
        UserDao userDao = buildDao(Map.of("Plynx", AppNameUtil.BLYNK));
        User plynxUser = new User(EMAIL, "otherHash", "Plynx", "local", "127.0.0.1", false, false);
        userDao.add(plynxUser);

        assertSame(plynxUser, userDao.getByName(EMAIL, "Plynx"));
    }

    @Test
    public void aliasDoesNotApplyToUnmappedAppNames() {
        UserDao userDao = buildDao(Map.of("Plynx", AppNameUtil.BLYNK));

        assertNull(userDao.getByName(EMAIL, "SomeOtherApp"));
        assertFalse(userDao.contains(EMAIL, "SomeOtherApp"));
    }

    @Test
    public void aliasDoesNotFindUnknownEmails() {
        UserDao userDao = buildDao(Map.of("Plynx", AppNameUtil.BLYNK));

        assertNull(userDao.getByName("unknown@example.com", "Plynx"));
        assertFalse(userDao.isUserExists("unknown@example.com", "Plynx"));
    }
}

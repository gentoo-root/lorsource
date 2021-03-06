/*
 * Copyright 1998-2010 Linux.org.ru
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package ru.org.linux.site;

import java.io.Serializable;
import java.net.URLEncoder;
import java.sql.*;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import ru.org.linux.spring.LoginController;
import ru.org.linux.util.StringUtil;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.apache.commons.codec.binary.Base64;
import org.jasypt.exceptions.EncryptionOperationNotPossibleException;
import org.jasypt.util.password.BasicPasswordEncryptor;
import org.jasypt.util.password.PasswordEncryptor;
import org.springframework.jdbc.support.JdbcUtils;

public class User implements Serializable {
  private static final int ANONYMOUS_LEVEL_SCORE = 50;

  private final String nick;
  private final int id;
  private final boolean canmod;
  private final boolean candel;
  private final boolean anonymous;
  private final boolean corrector;
  private final boolean blocked;
  private final String password;
  private final int score;
  private final int maxScore;
  private final String photo;
  private final String email;
  private final String fullName;
  private final int unreadEvents;

  private final boolean activated;
  public static final int CORRECTOR_SCORE = 100;
  private static final int BLOCK_MAX_SCORE = 400;
  private static final int BLOCK_SCORE = 200;
  public static final int VIEW_DELETED_SCORE = 100;

  public static final int MAX_NICK_LENGTH = 40;

  private static final long serialVersionUID = 69986652856916540L;

  private User(Connection con, String name) throws SQLException, UserNotFoundException {
    if (name == null) {
      throw new NullPointerException();
    }
    nick = name;

    if (!StringUtil.checkLoginName(name)) {
      throw new UserNotFoundException("<invalid name>");
    }

    PreparedStatement st = con.prepareStatement("SELECT id,candel,canmod,corrector,passwd,blocked,score,max_score,activated,photo,email,name,unread_events FROM users where nick=?");
    st.setString(1, name);

    ResultSet rs = st.executeQuery();

    if (!rs.next()) {
      throw new UserNotFoundException(name);
    }

    id = rs.getInt("id");
    canmod = rs.getBoolean("canmod");
    candel = rs.getBoolean("candel");
    corrector = rs.getBoolean("corrector");
    activated = rs.getBoolean("activated");
    blocked = rs.getBoolean("blocked");
    score = rs.getInt("score");
    maxScore = rs.getInt("max_score");
    fullName = rs.getString("name");
    String pwd = rs.getString("passwd");
    if (pwd == null) {
      pwd = "";
    }
    anonymous = "".equals(pwd);
    password = pwd;

    photo=rs.getString("photo");

    email = rs.getString("email");

    unreadEvents = rs.getInt("unread_events");

    rs.close();
    st.close();
  }

  private User(Connection con, int id) throws SQLException, UserNotFoundException {
    this.id = id;

    PreparedStatement st = con.prepareStatement("SELECT nick,score, max_score, candel,canmod,corrector,passwd,blocked,activated,photo,email,name,unread_events FROM users where id=?");
    st.setInt(1, id);

    ResultSet rs = st.executeQuery();

    if (!rs.next()) {
      throw new UserNotFoundException(id);
    }

    nick = rs.getString("nick");
    canmod = rs.getBoolean("canmod");
    corrector = rs.getBoolean("corrector");
    blocked = rs.getBoolean("blocked");
    candel = rs.getBoolean("candel");
    activated = rs.getBoolean("activated");
    String pwd = rs.getString("passwd");
    score = rs.getInt("score");
    maxScore = rs.getInt("max_score");
    fullName = rs.getString("name");
    if (pwd == null) {
      pwd = "";
    }
    password = pwd;
    anonymous = "".equals(pwd);
    photo=rs.getString("photo");
    email = rs.getString("email");
    unreadEvents = rs.getInt("unread_events");

    rs.close();
    st.close();
  }

  public int getId() {
    return id;
  }

  public String getNick() {
    return nick;
  }

  public void checkPassword(String password) throws BadPasswordException {
    if (blocked) {
      throw new BadPasswordException(nick);
    }

    if (password==null) {
      throw new BadPasswordException(nick);
    }

    if (anonymous && password.isEmpty()) {
      return;
    }

    if (!matchPassword(password)) {
      throw new BadPasswordException(nick);
    }
  }

  public boolean matchPassword(String password) {
    PasswordEncryptor encryptor = new BasicPasswordEncryptor();

    try {
      return encryptor.checkPassword(password, this.password);
    } catch (EncryptionOperationNotPossibleException ex) {
      return false;
    }
  }

  public void checkAnonymous() throws AccessViolationException {
    if (anonymous || blocked) {
      throw new AccessViolationException("Anonymous user");
    }
  }

  public void checkBlocked() throws AccessViolationException {
    if (blocked) {
      throw new AccessViolationException("Blocked user");
    }

    if (!activated) {
      throw new AccessViolationException("Not activated user");
    }

//    if (anonymous) {
//      throw new AccessViolationException("Anonymous user - disabled");
//    }
  }


  public void checkCommit() throws AccessViolationException {
    if (anonymous || blocked) {
      throw new AccessViolationException("Commit access denied for anonymous user");
    }
    if (!canmod) {
      throw new AccessViolationException("Commit access denied for user " + nick + " (" + id + ") ");
    }
  }

  public boolean isBlocked() {
    return blocked;
  }

  /**
   * Check if use is super-moderator
   *
   * @throws AccessViolationException if use is not super-moderator
   */
  public void checkDelete() throws AccessViolationException {
    if (anonymous || blocked) {
      throw new AccessViolationException("Delete access denied for anonymous user");
    }
    if (!candel) {
      throw new AccessViolationException("Delete access denied for user " + nick + " (" + id + ") ");
    }
  }

  public boolean canModerate() {
    return canmod;
  }

  public boolean isAdministrator() {
    return candel;
  }

  public boolean canCorrect() {
    return corrector && score>= CORRECTOR_SCORE;
  }

  public boolean isAnonymous() {
    return anonymous;
  }

  public String getMD5(String base) {
    return StringUtil.md5hash(base + password);
  }

  public String getActivationCode(String base) {
    return getActivationCode(base, nick, email);
  }

  public String getActivationCode(String base, String email) {
    return StringUtil.md5hash(base + ':' + nick + ':' + email);
  }

  public static String getActivationCode(String base, String nick, String email) {
    return StringUtil.md5hash(base + ':' + nick + ':' + email);
  }

  public int getScore() {
    if (anonymous) {
      return 0;
    } else {
      return score;
    }
  }

  public int getMaxScore() {
    if (anonymous) {
      return 0;
    } else {
      return maxScore;
    }
  }

  public String getStars() {
    return getStars(score, maxScore);
  }

  public static String getStars(int score, int maxScore) {
    StringBuilder out = new StringBuilder();

    if (score < 0) {
      score = 0;
    }
    if (score >= 600) {
      score = 599;
    }
    if (maxScore < 0) {
      maxScore = 0;
    }
    if (maxScore >= 600) {
      maxScore = 599;
    }

    if (maxScore < score) {
      maxScore = score;
    }

    int stars = (int) Math.floor(score / 100.0);
    int greyStars = (int) Math.floor(maxScore / 100.0) - stars;

    for (int i = 0; i < stars; i++) {
      out.append("<img src=\"/img/normal-star.gif\" width=9 height=9 alt=\"*\">");
    }

    for (int i = 0; i < greyStars; i++) {
      out.append("<img src=\"/img/grey-star.gif\" width=9 height=9 alt=\"#\">");
    }

    return out.toString();
  }

  public String getStatus() {
    if (score < ANONYMOUS_LEVEL_SCORE) {
      return "анонимный";
    } else if (score < 100 && maxScore < 100) {
      return "новый пользователь";
    } else {
      return getStars(score, maxScore);
    }
  }

  /**
   * Update lastlogin time in database
   * @param dbconn already opened database connection
   * @throws SQLException on database failure
   */
  public void updateUserLastlogin(Connection dbconn) throws SQLException {
    String sSql = "UPDATE users SET lastlogin=CURRENT_TIMESTAMP WHERE id=?";
    PreparedStatement pst = dbconn.prepareStatement(sSql);
    pst.setInt(1, id);
    pst.executeUpdate();
    pst.close();
  }

  public boolean isBlockable() {
    if (id==2) {
      return false;
    }

    if (canModerate()) {
      return false;
    }               

    return (maxScore < BLOCK_MAX_SCORE) && (score < BLOCK_SCORE);
  }

  public boolean isActivated() {
    return activated;
  }

  public String getPhoto() {
    return photo;
  }

  public void block(Connection db, User by, String reason) throws SQLException {
    Statement st = null;
    PreparedStatement pst = null;

    try {
      st = db.createStatement();
      st.executeUpdate("UPDATE users SET blocked='t' WHERE id=" + id);

      pst = db.prepareStatement("INSERT INTO ban_info (userid, reason, ban_by) VALUES (?, ?, ?)");
      pst.setInt(1, id);
      pst.setString(2, reason);
      pst.setInt(3, by.getId());
      pst.executeUpdate();

      updateCache(db);
    } finally {
      JdbcUtils.closeStatement(st);
      JdbcUtils.closeStatement(pst);
    }
  }

  public String resetPassword(Connection db) throws SQLException {
    String password = StringUtil.generatePassword();

    setPassword(db, password);

    return password;
  }

  public void setPassword(Connection db, String password) throws SQLException {
    PasswordEncryptor encryptor = new BasicPasswordEncryptor();

    String encryptedPassword = encryptor.encryptPassword(password);

    PreparedStatement st = null;

    try {
      st = db.prepareStatement("UPDATE users SET passwd=?,lostpwd = 'epoch' WHERE id=?");
      st.setString(1, encryptedPassword);
      st.setInt(2, id);
      st.executeUpdate();

      updateCache(db);
    } finally {
      if (st!=null) {
        st.close();
      }
    }
  }

  public List<Integer> deleteAllComments(Connection db, User moderator) throws SQLException, ScriptErrorException {
    Statement st = null;
    ResultSet rs = null;
    CommentDeleter deleter = null;
    List<Integer> deleted = new LinkedList<Integer>();

    try {
      // Delete user topics
      PreparedStatement lock = db.prepareStatement("SELECT id FROM topics WHERE userid=? AND not deleted FOR UPDATE");
      PreparedStatement st1 = db.prepareStatement("UPDATE topics SET deleted='t',sticky='f' WHERE id=?");
      PreparedStatement st2 = db.prepareStatement("INSERT INTO del_info (msgid, delby, reason, deldate) values(?,?,?, CURRENT_TIMESTAMP)");
      lock.setInt(1, id);
      st2.setInt(2, moderator.id);
      st2.setString(3,"Автоматически: удаление всех коментариев");
      ResultSet lockResult = lock.executeQuery(); // lock another delete on this row
      while (lockResult.next()) {
        int mid = lockResult.getInt("id");
        st1.setInt(1,mid);
        st2.setInt(1,mid);
        st1.executeUpdate();
        st2.executeUpdate();
      }
      st1.close();
      st2.close();
      lockResult.close();
      lock.close();

      // Delete user comments
      deleter = new CommentDeleter(db);

      st = db.createStatement();

      rs = st.executeQuery("SELECT id FROM comments WHERE userid="+id+" AND not deleted ORDER BY id DESC FOR update");

      while(rs.next()) {
        int msgid = rs.getInt("id");

        deleted.add(msgid);

        deleted.addAll(deleter.deleteReplys(msgid, moderator, false));
        
        deleter.deleteComment(msgid, "4.7 Flood (auto)", moderator, -20);
      }
    } finally {
      if (deleter!=null) {
        deleter.close();
      }

      if (rs!=null) {
        rs.close();
      }

      if (st!=null) {
        st.close();
      }
    }

    return deleted;
  }

  public static User getUser(Connection con, String name) throws SQLException, UserNotFoundException {
    Cache cache = CacheManager.create().getCache("Users");

    User user = new User(con, name);

    String cacheId = "User?id="+ user.id;

    cache.put(new Element(cacheId, user));
    
    return user;
  }

  public static User getUser(Connection db, int id) throws SQLException, UserNotFoundException {
    return getUser(db, id, false);
  }

  public static User getUserCached(Connection db, int id) throws SQLException, UserNotFoundException {
    return getUser(db, id, true);
  }

  private static User getUser(Connection db, int id, boolean useCache) throws SQLException, UserNotFoundException {
    Cache cache = CacheManager.create().getCache("Users");

    String cacheId = "User?id="+id;

    User res = null;

    if (useCache) {
      Element element = cache.get(cacheId);
      
      if (element!=null) {
        res = (User) element.getObjectValue();
      }
    }

    if (res==null) {
      res = new User(db, id);
      cache.put(new Element(cacheId, res));
    }

    return res;
  }

  public boolean isAnonymousScore() {
    return anonymous || blocked || score<ANONYMOUS_LEVEL_SCORE;
  }

  public void acegiSecurityHack(HttpServletResponse response, HttpSession session) {
    String username = nick;
    //String cookieName = "ACEGI_SECURITY_HASHED_REMEMBER_ME_COOKIE"; //ACEGI_SECURITY_HASHED_REMEMBER_ME_COOKIE_KEY;
    String cookieName = LoginController.ACEGI_COOKIE_NAME; //ACEGI_SECURITY_HASHED_REMEMBER_ME_COOKIE_KEY;
    long tokenValiditySeconds = 1209600; // 14 days
    String key = "jam35Wiki"; // from applicationContext-acegi-security.xml
    long expiryTime = System.currentTimeMillis() + (tokenValiditySeconds * 1000);

    // construct token to put in cookie; format is:
    // username + ":" + expiryTime + ":" + Md5Hex(username + ":" +
    // expiryTime + ":" + password + ":" + key)

    String signatureValue = StringUtil.md5hash(username + ':' + expiryTime + ':' + password + ':' + key);
    String tokenValue = username + ':' + expiryTime + ':' + signatureValue;
    String tokenValueBase64 = new String(Base64.encodeBase64(tokenValue.getBytes()));

    // Add remember me cookie
    Cookie acegi = new Cookie(cookieName, tokenValueBase64);
    acegi.setMaxAge(Long.valueOf(expiryTime).intValue());
    acegi.setPath("/wiki");
    response.addCookie(acegi);

    // Remove ACEGI_SECURITY_CONTEXT and session
    session.removeAttribute("ACEGI_SECURITY_CONTEXT"); // if any
  }

  private void updateCache(Connection db) throws SQLException {
    try {
      getUser(db, id);
    } catch (UserNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public void changeScore(Connection db, int delta) throws SQLException {
    PreparedStatement st = null;
    try {
      st = db.prepareStatement("UPDATE users SET score=score+? WHERE id=?");
      st.setInt(1, delta);
      st.setInt(2, id);
      st.executeUpdate();

      updateCache(db);
    } finally {
      if (st!=null) {
        st.close();
      }
    }
  }

  public boolean isCorrector() {
    return corrector;
  }

  public static void checkNick(String nick) throws BadInputException {
    if (nick==null || !StringUtil.checkLoginName(nick)) {
      throw new BadInputException("некорректное имя пользователя");
    }

    if (nick.length() > MAX_NICK_LENGTH) {
      throw new BadInputException("слишком длинное имя пользователя");
    }
  }

  public String getGravatar(String avatarStyle, int size, boolean secure) {
    String nonExist;

    if ("empty".equals(avatarStyle)) {
      if (secure) {
        nonExist = URLEncoder.encode("https://www.linux.org.ru/img/p.gif");
      } else {
        nonExist = URLEncoder.encode("http://www.linux.org.ru/img/p.gif");
      }
    } else {
      nonExist = avatarStyle;
    }

    String grUrl = secure?"https://secure.gravatar.com/avatar/":"http://www.gravatar.com/avatar/";

    return grUrl
      + StringUtil.md5hash(email.toLowerCase())
      + "?s="+size+"&amp;r=g&amp;d="+nonExist;
  }

  public String getEmail() {
    return email;
  }

  public boolean hasGravatar() {
    return email!=null;
  }

  public String getUserinfo(Connection db) throws SQLException {
    PreparedStatement st = db.prepareStatement("SELECT userinfo FROM users where id=?");
    st.setInt(1, id);

    ResultSet rs = st.executeQuery();
    rs.next();

    String userinfo = rs.getString("userinfo");

    if (userinfo==null) {
      return "";
    } else {
      return userinfo;
    }
  }

  public void setUserinfo(Connection db, String text) throws SQLException {
    PreparedStatement st = db.prepareStatement("UPDATE users SET userinfo=? where id=?");
    st.setString(1, text);
    st.setInt(2, id);

    st.executeUpdate();
  }

  public static void setUserinfo(Connection db, int id, String text) throws SQLException {
    PreparedStatement st = db.prepareStatement("UPDATE users SET userinfo=? where id=?");
    st.setString(1, text);
    st.setInt(2, id);

    st.executeUpdate();
  }

  public String getName() {
    return fullName;
  }

  public int getUnreadEvents() {
    return unreadEvents;
  }

  public void resetUnreadEvents(Connection db) throws SQLException {
    PreparedStatement st = db.prepareStatement("UPDATE users SET unread_events=0 where id=?");
    try {
      st.setInt(1, id);

      st.executeUpdate();
    } finally {
      JdbcUtils.closeStatement(st);
    }
  }
}

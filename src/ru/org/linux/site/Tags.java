package ru.org.linux.site;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import gnu.regexp.RE;
import gnu.regexp.REException;
import org.apache.commons.lang.StringUtils;

public class Tags{
  private static final RE tagRE;  
  
  static {
    try {
      tagRE = new RE("([\\w\\ ]+)", RE.REG_ICASE);
    } catch (REException e) {
      throw new RuntimeException(e);
    }
  }                          
  
  private final ArrayList<String> tags;
  private static final int TOP_TAGS_COUNT = 50;

  private static synchronized int getOrCreateTag(Connection con, String tag) throws SQLException {
    PreparedStatement st2 = con.prepareStatement("SELECT id FROM tags_values WHERE value=?");
    st2.setString(1,tag);
    ResultSet rs = st2.executeQuery();
    int id;

    if (rs.next()) {
      id = rs.getInt("id");
    } else {
      PreparedStatement st = con.prepareStatement("INSERT INTO tags_values (value) VALUES(?)");
      st.setString(1,tag);
      st.executeUpdate();
      st.close();
      
      rs = st2.executeQuery();
      rs.next();
      id = rs.getInt("id");
    }

    rs.close();
    st2.close();

    return id;
  }

  private Tags(Connection con, int msgid) throws SQLException {
    tags = new ArrayList<String>();
    
    PreparedStatement st = con.prepareStatement("SELECT tags_values.value FROM tags, tags_values WHERE tags.msgid=? AND tags_values.id=tags.tagid");
    st.setInt(1, msgid);

    ResultSet rs = st.executeQuery();

    while (rs.next()) {
      tags.add(rs.getString("value"));
    }
    st.close();
  }

  public String toString() {
    if (tags==null || tags.isEmpty()) {
      return "";
    }
    String str = "";

    for (String tag : tags) {
      str += (str.length() > 0 ? "," : "") + tag;
    }
    
    return str;
  }

  public ArrayList<String> getTags() {
    return tags;
  }

  public static Set<String> getTopTags(Connection con) throws SQLException {
    Set<String> set = new TreeSet<String>();
    PreparedStatement st = con.prepareStatement("SELECT counter,value FROM tags_values WHERE counter>1 ORDER BY counter DESC LIMIT " + TOP_TAGS_COUNT);
    ResultSet rs = st.executeQuery();

    while (rs.next()) {
      set.add(rs.getString("value"));
    }

    return set;
  }

  public static ArrayList<String> getMessageTags(Connection con, int msgid) throws SQLException {
    return new Tags(con, msgid).getTags();
  }

  public static void checkTag(String tag) throws UserErrorException {
    // ��������� ����: ������ �����/�����/�������, ������� ������������, �������, ����������� � <>
    if (!tagRE.isMatch(tag)) {
      throw new UserErrorException("Invalid tag: '"+tag+"'");
    }
  }

  public static void updateCounters(Connection con, List<String> oldTags, List<String> newTags) throws SQLException {
    PreparedStatement stInc = con.prepareStatement("UPDATE tags_values SET counter=counter+1 WHERE id=?");
    PreparedStatement stDec = con.prepareStatement("UPDATE tags_values SET counter=counter-1 WHERE id=?");

    if (oldTags==null) {
      oldTags = Collections.emptyList();
    }

    for (String tag : newTags) {
      if (!oldTags.contains(tag)) {
        int id = getOrCreateTag(con, tag);
        stInc.setInt(1, id);
        stInc.executeUpdate();
      }
    }

    for (String tag : oldTags) {
      if (!newTags.contains(tag)) {
        int id = getOrCreateTag(con, tag);
        stDec.setInt(1, id);
        stDec.executeUpdate();
      }
    }
  }

  public static List<String> parseTags(String tags) throws UserErrorException {
    Set<String> tagSet = new HashSet<String>();

    // ���� ����������� ������ ��� �������
    tags = tags.replaceAll("\\|",",");
    String [] tagsArr = tags.split(",");

    if (tagsArr.length==0) {
      return Collections.emptyList();
    }

    for (String aTagsArr : tagsArr) {
      String tag = StringUtils.stripToNull(aTagsArr.toLowerCase());
      // ������ ��� - �����������
      if (tag == null) {
        continue;
      }

      // ��������� ����: ������ �����/�����/�������, ������� ������������, �������, ����������� � <>
      checkTag(tag);

      tagSet.add(tag);
    }

    return new ArrayList<String>(tagSet);
  }

  public static String getEditTags(Collection<String> tags) {
    StringBuilder out = new StringBuilder();
    boolean first = true;

    for (String tag : tags) {
      if (!first) {
        out.append(", ");
      }
      out.append("<a onclick=\"addTag('").append(tag).append("')\">");
      out.append(tag);
      out.append("</a>");
      first = false;
    }

    return out.toString();
  }

  public static void updateTags(Connection con, int msgid, List<String> tagList) throws SQLException {
    List<String> oldTags = Tags.getMessageTags(con, msgid);

    PreparedStatement insertStatement = con.prepareStatement("INSERT INTO tags VALUES(?,?)");
    PreparedStatement deleteStatement = con.prepareStatement("DELETE FROM tags WHERE msgid=? and tagid=?");

    insertStatement.setInt(1, msgid);
    deleteStatement.setInt(1, msgid);

    for (String tag : tagList) {
      if (!oldTags.contains(tag)) {
        int id = getOrCreateTag(con, tag);

        insertStatement.setInt(2, id);
        insertStatement.executeUpdate();
      }
    }

    for (String tag : oldTags) {
      if (!tagList.contains(tag)) {
        int id = getOrCreateTag(con, tag);

        deleteStatement.setInt(2, id);
        deleteStatement.executeUpdate();
      }
    }

    insertStatement.close();
    deleteStatement.close();
  }

  public static String getPlainTags(Connection con, int msgid) throws SQLException {
    return new Tags(con,msgid).toString();
  }
  
  public static String getTagLinks(Connection con, int msgid) throws SQLException {
    StringBuilder buf = new StringBuilder();
    Tags tags = new Tags(con, msgid);
    ArrayList<String> mtags = tags.getTags();
    if (mtags.isEmpty()) {
      return "";
    }
    for (String mtag : mtags) {
      if (buf.length() > 0) {
        buf.append(", ");
      }

      try {
        buf.append("<a href=\"view-news.jsp?section=1&amp;tag=").append(URLEncoder.encode(mtag, "UTF-8")).append("\">").append(mtag).append("</a>");
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
    }
    
    return buf.toString();
  }
}
/*
 * Copyright 1998-2009 Linux.org.ru
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

package ru.org.linux.spring;

import java.net.URLEncoder;
import java.sql.*;
import java.util.*;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.danga.MemCached.MemCachedClient;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import ru.org.linux.site.LorDataSource;
import ru.org.linux.site.MemCachedSettings;
import ru.org.linux.site.Template;
import ru.org.linux.site.User;

@Controller
public class ShowRepliesController {
  @RequestMapping("/show-replies.jsp")
  public ModelAndView showReplies(
    HttpServletRequest request,
    HttpServletResponse response,
    @RequestParam("nick") String nick,
    @RequestParam(value = "offset", required = false) Integer offsetObject
  ) throws Exception {
    User.checkNick(nick);

    Map<String, Object> params = new HashMap<String, Object>();
    params.put("nick", nick);
    Template tmpl = Template.getTemplate(request);

    int offset = 0;
    if (offsetObject!=null) {
      offset = offsetObject;
    }

    if (offset < 0) {
      offset = 0;
    }

    boolean firstPage = ! (offset > 0);
    int topics = tmpl.getProf().getInt("topics");

    params.put("firstPage", firstPage);
    params.put("topics", topics);
    params.put("offset", offset);

    /* define timestamps for caching */
    long time = System.currentTimeMillis();
    int delay = firstPage ? 90 : 60*60;
    response.setDateHeader("Expires", time + 1000 * delay);

    MemCachedClient mcc=MemCachedSettings.getClient();
    String cacheKey = MemCachedSettings.getId(
             "show-replies?id="+ URLEncoder.encode(nick)+"&offset="+offset
    );
    List<TopicsListItem> list = (List<TopicsListItem>) mcc.get(cacheKey);
    int messages = tmpl.getProf().getInt("messages");

    if (list == null) {
      Connection db = null;

      try {
        db = LorDataSource.getConnection();

        User user = User.getUser(db, nick);

        list = new ArrayList<TopicsListItem>();

        PreparedStatement pst = db.prepareStatement(
          "SELECT topics.title as subj, sections.name, groups.title as gtitle, lastmod, topics.userid, topics.id as msgid, topics.deleted, topics.stat1, topics.stat3, topics.stat4, topics.sticky, " +
            " comments.id AS cid, " +
            "comments.postdate AS cDate, " +
            "comments.userid AS cAuthor " +
            "FROM sections, groups, topics, comments, comments AS parents " +
            "WHERE sections.id=groups.section AND groups.id=topics.groupid " +
            "AND comments.topic=topics.id AND parents.userid = ? " +
            " AND comments.replyto = parents.id " +
            "AND NOT comments.deleted ORDER BY cDate DESC LIMIT " + topics +
            " OFFSET " + offset
        );

        pst.setInt(1, user.getId());
        ResultSet rs = pst.executeQuery();

        while (rs.next()) {
          list.add(new MyTopicsListItem(rs, messages));
        }

        rs.close();

        mcc.add(cacheKey, list, new Date(time + 1000L * delay));
      } finally {
        if (db != null) {
          db.close();
        }
      }
    }

    params.put("topicsList", list);

    return new ModelAndView("show-replies", params);
  }

  public static class MyTopicsListItem extends TopicsListItem {
    private final int cid;
    private final int cAuthor;
    private final Timestamp cDate;

    public MyTopicsListItem(ResultSet rs, int messages) throws SQLException {
      super(rs, messages);

      cid = rs.getInt("cid");
      cAuthor = rs.getInt("cAuthor");
      cDate = rs.getTimestamp("cDate");
    }

    public int getCid() {
      return cid;
    }

    public int getCommentAuthor() {
      return cAuthor;
    }

    public Timestamp getCommentDate() {
      return cDate;
    }
  }

}

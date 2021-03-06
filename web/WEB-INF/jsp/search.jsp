<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ page contentType="text/html; charset=utf-8"%>
<%@ page import="ru.org.linux.site.SearchViewer"  %>
<%@ taglib tagdir="/WEB-INF/tags" prefix="lor" %>
<%--
  ~ Copyright 1998-2010 Linux.org.ru
  ~    Licensed under the Apache License, Version 2.0 (the "License");
  ~    you may not use this file except in compliance with the License.
  ~    You may obtain a copy of the License at
  ~
  ~        http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~    Unless required by applicable law or agreed to in writing, software
  ~    distributed under the License is distributed on an "AS IS" BASIS,
  ~    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~    See the License for the specific language governing permissions and
  ~    limitations under the License.
  --%>
<%--@elvariable id="result" type="java.util.List<ru.org.linux.site.SearchItem>"--%>
<%--@elvariable id="boolean" type="java.lang.Boolean"--%>
<%--@elvariable id="initial" type="java.lang.Boolean"--%>
<%--@elvariable id="q" type="java.lang.String"--%>
<%--@elvariable id="template" type="ru.org.linux.site.Template"--%>
<%--@elvariable id="usertopic" type="java.lang.String"--%>
<%--@elvariable id="time" type="java.lang.Long"--%>
<%--@elvariable id="searchTime" type="java.lang.Long"--%>
<%--@elvariable id="numFound" type="java.lang.Long"--%>
<%--@elvariable id="date" type="ru.org.linux.site.SearchViewer.SearchInterval"--%>

<jsp:include page="/WEB-INF/jsp/head.jsp"/>
<title>Поиск по сайту
  <c:if test="${not initial}">
    - <c:out value="${q}" escapeXml="true"/>
  </c:if>
</title>
<jsp:include page="/WEB-INF/jsp/header.jsp"/>

<H1>Поиск по сайту</h1>
<h2>Поисковая система сайта</h2>

<%
  int include = (Integer) request.getAttribute("include");
  int section = (Integer) request.getAttribute("section");
  int sort = (Integer) request.getAttribute("sort");  
%>

<FORM METHOD=GET ACTION="search.jsp">
<INPUT TYPE="text" NAME="q" SIZE=50 maxlength="250" VALUE="${fn:escapeXml(q)}">
  <input TYPE="submit" VALUE="Поиск"><BR>
  
  <p>
  <select name="include">
    <option value="topics" <%= (include==SearchViewer.SEARCH_TOPICS)?"selected":"" %>>только темы</option>
    <option value="all" <%= (include==SearchViewer.SEARCH_ALL)?"selected":"" %>>темы и комментарии</option>
  </select>
  <label for="id_noinclude_title">Не искать по заголовкам сообщений</label>
    <c:if test="${noinclude_title}">
      <INPUT type="checkbox" id="id_noinclude_title" NAME="noinclude_title" checked><br>
    </c:if>
    <c:if test="${!noinclude_title}">
      <INPUT type="checkbox" id="id_noinclude_title" NAME="noinclude_title"><br>
    </c:if>

  <label>За:
  <select name="date">
    <c:forEach var="interval" items="<%= SearchViewer.SearchInterval.values() %>">
      <c:if test="${date == interval}">
        <option value="${interval}" selected>${interval.title}</option>
      </c:if>
      <c:if test="${date != interval}">
        <option value="${interval}">${interval.title}</option>
      </c:if>
    </c:forEach>
  </select></label>
<br>
  <label>Раздел:
  <select name="section">
    <option value="1" <%= (section == 1) ? "selected" : "" %>>новости</option>
    <option value="2" <%= (section == 2) ? "selected" : "" %>>форум</option>
    <option value="3" <%= (section == 3) ? "selected" : "" %>>галерея</option>
    <option value="0" <%= (section == 0) ? "selected" : "" %>>все</option>
  </select></label>

  <label for="search_username">Пользователь:</label>
  <INPUT TYPE="text" NAME="username" id="search_username" SIZE=20 VALUE="${fn:escapeXml(username)}">
  <br>
    <label for="id_user_topic">В темах пользователя</label>
    <c:if test="${usertopic}">
      <INPUT type="checkbox" id="id_user_topic" NAME="usertopic" checked><br>
    </c:if>
    <c:if test="${!usertopic}">
      <INPUT type="checkbox" id="id_user_topic" NAME="usertopic"><br>
    </c:if>

  <label>Сортировать
  <select name="sort">
  <option value="<%= SearchViewer.SORT_DATE %>" <%= (sort==SearchViewer.SORT_DATE)?"selected":"" %>>по дате</option>

  <option value="<%= SearchViewer.SORT_R %>" <%= (sort==SearchViewer.SORT_R)?"selected":"" %>>по релевантности</option>
  </select></label>

  <br>
</form>

<c:if test="${not initial}">
  <h1>Результаты поиска</h1>
  <p>
  Всего найдено ${numFound} результатов, показаны ${fn:length(result)}
  </p>
  <div class="messages">
  <div class="comment">
    <c:forEach items="${result}" var="item">
      <div class=msg>
        <div class="msg_body">
          <h2><a href="${item.url}"><c:out escapeXml="true" value="${item.title}"/></a></h2>

          <p>${item.message}</p>

          <div class=sign>
            <lor:sign postdate="${item.postdate}" shortMode="${template.mobile}"
                      user="${item.user}"/>
          </div>
        </div>
      </div>
    </c:forEach>
  </div>
  </div>

  <p>
    <i>
      Общее время запроса ${time} ms (время поиска: ${searchTime} ms)
    </i>
  </p>
</c:if>

<c:if test="${initial}">
  <h2>Поиск через Google</h2>
  <jsp:include page="/WEB-INF/jsp/${template.style}/google-search.jsp"/>
</c:if>

<jsp:include page="/WEB-INF/jsp/footer.jsp"/>

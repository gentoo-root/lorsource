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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.javabb.bbcode.BBCodeProcessor;

public class PreparedEditInfo {
  private final EditInfoDTO editInfo;
  private final boolean original;
  private final User editor;
  private final String message;
  private final boolean current;
  private final String title;
  private final List<String> tags;

  public PreparedEditInfo(
    Connection db,
    EditInfoDTO editInfo,
    String message,
    String title,
    List<String> tags,
    boolean current,
    boolean original
  ) throws UserNotFoundException, SQLException {
    this.editInfo = editInfo;
    this.original = original;

    editor = User.getUserCached(db, editInfo.getEditor());

    BBCodeProcessor proc = new BBCodeProcessor();
    
    if (message!=null) {
      this.message = proc.preparePostText(db, message);
    } else {
      this.message = null;
    }

    this.title = title;

    this.current = current;

    this.tags = tags;
  }

  public EditInfoDTO getEditInfo() {
    return editInfo;
  }

  public User getEditor() {
    return editor;
  }

  public String getMessage() {
    return message;
  }

  public boolean isCurrent() {
    return current;
  }

  public String getTitle() {
    return title;
  }

  public boolean isOriginal() {
    return original;
  }

  public List<String> getTags() {
    return tags;
  }

  public static List<PreparedEditInfo> build(Connection db, Message message) throws SQLException, UserNotFoundException, UserErrorException {
    List<EditInfoDTO> editInfoDTOs = message.loadEditInfo(db);
    List<PreparedEditInfo> editInfos = new ArrayList<PreparedEditInfo>(editInfoDTOs.size());

    String currentMessage = message.getMessage();
    String currentTitle = message.getTitle();
    List<String> currentTags = Tags.getMessageTags(db, message.getMessageId());

    for (int i = 0; i<editInfoDTOs.size(); i++) {
      EditInfoDTO dto = editInfoDTOs.get(i);

      editInfos.add(
        new PreparedEditInfo(
          db,
          dto,
          dto.getOldmessage()!=null ? currentMessage : null,
          dto.getOldtitle()!=null ? currentTitle : null,
          dto.getOldtags()!=null ? currentTags : null,
          i==0,
          false
        )
      );

      if (dto.getOldmessage() !=null) {
        currentMessage = dto.getOldmessage();
      }

      if (dto.getOldtitle() != null) {
        currentTitle = dto.getOldtitle();
      }

      if (dto.getOldtags()!=null) {
        currentTags = Tags.parseTags(dto.getOldtags());
      }
    }

    if (!editInfoDTOs.isEmpty()) {
      EditInfoDTO current = EditInfoDTO.createFromMessage(db, message);

      editInfos.add(new PreparedEditInfo(db, current, currentMessage, currentTitle, currentTags, false, true));
    }

    return editInfos;
  }
}

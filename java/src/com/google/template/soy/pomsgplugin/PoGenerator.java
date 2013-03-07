/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.google.template.soy.pomsgplugin;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.IndentedLinesBuilder;
import com.google.template.soy.internal.base.CharEscaper;
import com.google.template.soy.internal.base.CharEscapers;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralCaseSpec;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralPart;
import com.google.template.soy.msgs.restricted.SoyMsgSelectPart;
import java.util.List;

import java.util.Map;

import javax.annotation.Nullable;

/**
 *
 * @author Stephen Searles <stephen@leapingbrain.com>
 */
public class PoGenerator {

   private PoGenerator() {}


  /** Make some effort to use correct XLIFF datatype values. */
  private static final Map<String, String> CONTENT_TYPE_TO_XLIFF_DATATYPE_MAP =
      ImmutableMap.<String, String>builder()
          .put("text/plain", "plaintext")
          .put("text/html", "html")
          .put("application/xhtml+xml", "xhtml")
          .put("application/javascript", "javascript")
          .put("text/css", "css")
          .put("text/xml", "xml")
          .build();


  /**
   * Generates the output PO file content for a given SoyMsgBundle.
   *
   * @param msgBundle The SoyMsgBundle to process.
   * @param sourceLocaleString The source language/locale string of the messages.
   * @param targetLocaleString The target language/locale string of the messages (optional). If
   *     specified, the resulting XLIFF file will specify this target language and will contain
   *     empty 'target' tags. If not specified, the resulting XLIFF file will not contain target
   *     language and will not contain 'target' tags.
   * @return The generated PO file content.
   */
  static CharSequence generatePo(
      SoyMsgBundle msgBundle, String sourceLocaleString, @Nullable String targetLocaleString) {
    IndentedLinesBuilder ilb = new IndentedLinesBuilder(2);

    for (SoyMsg msg : msgBundle) {

      // Description and meaning.
      String desc = msg.getDesc();
      if (desc != null && desc.length() > 0) {
        ilb.appendLine("# Description: ", desc);
      }
      String meaning = msg.getMeaning();
      if (meaning != null && meaning.length() > 0) {
        ilb.appendLine("# Meaning: ", meaning);
      }

      // Begin message
      ilb.appendLine("#: id=".concat(Long.toString(msg.getId())));
      ilb.appendLine("#: type=".concat(msg.getContentType()));

      StringBuilder singular = new StringBuilder();
      singular.append("msgid \"");

      boolean useSingular = true;

      for (SoyMsgPart msgPart : msg.getParts()) {
        if (msgPart instanceof SoyMsgPluralPart) {
          pluralMessage((SoyMsgPluralPart) msgPart, ilb);
          useSingular = false;
          break;
        } else {
          singular.append(message(msgPart));
        }
      }

      if (useSingular) {
        ilb.append(singular.toString());
        ilb.appendLineEnd("\"");
      } else if (!singular.toString().equalsIgnoreCase("msgid \"")) {
        throw new PoException("No message content is allowed before or after a plural block. Found: ".concat(singular.toString().substring(6)));
      }
      ilb.appendLineEnd();

    }

    return ilb;
  }


  static String message(SoyMsgPart msgPart) {
    if (msgPart instanceof SoyMsgRawTextPart) {
      return ((SoyMsgRawTextPart) msgPart).getRawText();
    } else if (msgPart instanceof SoyMsgPluralPart) {
      throw new PoException("PO generation does not support embedded {plural}.");
    } else if (msgPart instanceof SoyMsgSelectPart) {
      throw new PoException("PO generatioin does not support select blocks.");
    } else {
      String placeholderName = ((SoyMsgPlaceholderPart) msgPart).getPlaceholderName();
      return "{$".concat(placeholderName).concat("}");
    }
  }

  static void pluralMessage(SoyMsgPluralPart msgPart, IndentedLinesBuilder ilb) throws PoException {
    for ( Pair<SoyMsgPluralCaseSpec, List<SoyMsgPart>> pluralPart : ((SoyMsgPluralPart) msgPart).getCases()) {

      StringBuilder currentMessage = new StringBuilder();

      if (pluralPart.first.getType() == SoyMsgPluralCaseSpec.Type.EXPLICIT && pluralPart.first.getExplicitValue() == 1) {
        currentMessage.append("msgid \"");
        for (SoyMsgPart pluralSubPart : pluralPart.second) {
            currentMessage.append(message(pluralSubPart));
        }
        currentMessage.append("\"");
      } else if (pluralPart.first.getType() == SoyMsgPluralCaseSpec.Type.OTHER) {
        currentMessage.append("msgid_plural \"");
        for (SoyMsgPart pluralSubPart : pluralPart.second) {
          currentMessage.append(message(pluralSubPart));
        }
        currentMessage.append("\"");
      } else {
        throw new PoException("PO only supports singular and plural variants, {case 1} and {default}, respectively.");
      }

      ilb.appendLineEnd(currentMessage.toString());
    }
  }

}

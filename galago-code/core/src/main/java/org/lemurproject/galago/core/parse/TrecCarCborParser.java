package org.lemurproject.galago.core.parse;

import edu.unh.cs.treccar_v2.Data;
import edu.unh.cs.treccar_v2.read_data.DeserializeData;
import org.lemurproject.galago.core.types.DocumentSplit;
import org.lemurproject.galago.utility.Parameters;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Parser for the TREC Complex Answer Retrieval CBOR data, the pages and paragraphs.
 * <p>
 * http://trec-car.cs.unh.edu/
 *
 * @author jdalton
 */
public class TrecCarCborParser extends DocumentStreamParser {

  BufferedInputStream inputStream;

  // An iterator for either paragraphs or pages; both will not be used together.
  Iterator dataIterator;

  // Categories of document types for the CAR data.
  public enum DocumentType {
    PARAGRAPH, PAGES
  }

  // The type of CBOR document being parsed.
  private DocumentType documentType = DocumentType.PARAGRAPH;

  private int numDocuments = 1000;

  /**
   * Creates a new TrecCarCborParser from Galago parameters.
   * Note: Requires "documentType" parameter to be specified with one of the valid
   * enum values defined above.
   */
  public TrecCarCborParser(DocumentSplit split, Parameters p) throws IOException {
    super(split, p);

    // Default mode is paragraph corpus.
    if (p.containsKey("documentType")) {
      documentType = DocumentType.valueOf(p.getAsString("documentType"));
    }
    this.inputStream = getBufferedInputStream(split);
    String filename = getFileName(split);
    System.out.println("Starting CBOR parse with type: " + documentType.name());
    switch (documentType) {
      case PARAGRAPH:
        dataIterator = DeserializeData.iterParagraphs(this.inputStream);
        break;
      case PAGES:
        dataIterator = DeserializeData.iterAnnotations(this.inputStream);
        break;
      default:
        throw new IllegalArgumentException("Invalid corpus data.");
    }
  }

  /**
   * Parses the next document from the CBOR stream.
   *
   * @return Document for Galago; returns null if there is no next document.
   * @throws IOException
   */
  @Override
  public Document nextDocument() throws IOException {
    if (!dataIterator.hasNext()) {
      return null;
    }
    numDocuments++;
  //  if (numDocuments > 50000) return null;
    switch (documentType) {
      case PARAGRAPH:
        return nextParagraph();
      case PAGES:
        return nextPage();
      default:
        throw new IllegalArgumentException("Invalid corpus data.");
    }
  }

  private Document nextParagraph() {
    Data.Paragraph paragraph = (Data.Paragraph) dataIterator.next();
    String paragraphText = contentFromParagraph(paragraph);
    Document document = new Document(paragraph.getParaId(), paragraphText);
    return document;
  }


  private Document nextPage() {
    Data.Page page = (Data.Page) dataIterator.next();
    StringBuilder buffer = new StringBuilder();
    createField("title", page.getPageName(), buffer, false, true);
    createField("title-exact", page.getPageName(), buffer, false, false);

    for (Data.PageSkeleton skel : page.getSkeleton()) {
      recurseArticle(skel, buffer);
    }

    Data.PageMetadata metadata = page.getPageMetadata();

    List<String> cleanedDisambiguation = new ArrayList();
    for (String disambiguation : metadata.getDisambiguationNames()) {
      String cleaned = disambiguation.replace(" (disambiguation)", "");
      cleanedDisambiguation.add(cleaned);
    }


    List<String> redirectTypes = new ArrayList();
    for (String redirect :  metadata.getRedirectNames()) {
      // Note: > 0 explicitly to not get names that are all in parenthesis.
      String type = extractTypeFromName(redirect);
      if (type != null) {
        redirectTypes.add(type);
      }
    }
    String nameType = extractTypeFromName(page.getPageName());
    if (nameType != null) {
      redirectTypes.add(nameType);
    }
    createField("disambiguation-name", cleanedDisambiguation, buffer, true);
    createField("disambiguation-id", metadata.getDisambiguationIds(), buffer, false);
    createField("redirect-types", redirectTypes, buffer, false);

    List<String> cleaned_categories = new ArrayList();
    for (String category : metadata.getCategoryNames()) {
      cleaned_categories.add(category.replace("Category:", "").replace("enwiki:", ""));
    }
    createField("category", cleaned_categories, buffer, true);
    createField("category-id", metadata.getCategoryIds(), buffer, false);

    createField("inlink", metadata.getInlinkIds(), buffer, false);
    createAnchorFieldFromCounts(metadata.getInlinkAnchors(), "at", buffer, true);

    createField("redirect", metadata.getRedirectNames(), buffer, true);
    createField("redirect-exact", metadata.getRedirectNames(), buffer, false);

    List<String> listOf = new ArrayList();
    for (String inlink : metadata.getInlinkIds()) {
      if (inlink.startsWith("enwiki:List%20of%20")) {
        listOf.add(inlink.replace("enwiki:List%20of%20", "").replaceAll("%20", " "));
      }
    }
    createField("list-type", listOf, buffer, true);
    createField("list-type-exact", listOf, buffer, false);

    Document result = new Document(page.getPageId(), buffer.toString());
    result.metadata.put("title", page.getPageName());
    result.metadata.put("inlinkCount", metadata.getInlinkIds().size() + "");
//    res.metadata.put("kbName", kbName);
//    res.metadata.put("kbId", kbId);
//    res.metadata.put("kbType", kbType);
//    res.metadata.put("fbname", freebaseNames);
      result.metadata.put("category", String.join("\t", metadata.getCategoryIds()));
      result.metadata.put("redirect", String.join("\t", metadata.getRedirectNames()));
      result.metadata.put("redirect-types", String.join("\t", redirectTypes));
//    res.metadata.put("fbtype", freebaseTypes);
      result.metadata.put("inlink", String.join("\t", metadata.getInlinkIds()));
//      result.metadata.put("anchor", String.join("\t", metadata.getInlinkAnchors()));
      result.metadata.put("disambiguationId", String.join("\t", metadata.getDisambiguationIds()));
      result.metadata.put("listTypes", String.join("\t", listOf));

//    res.metadata.put("xml", wikiXml);
//    res.metadata.put("externalLinkCount", externalInlinkCount + "");
//    res.metadata.put("contextLinks", contextLinks);

    return result;

  }

  private static void createAnchorFieldFromCounts(Iterable<Data.ItemWithFrequency<String>> anchors, String fieldName,
                                                    StringBuilder buffer, boolean tokenize) {
    for (Data.ItemWithFrequency<String> anchor : anchors) {
      int frequency = anchor.getFrequency();
      for (int i=0; i < frequency; i++) {
        start(fieldName, buffer, tokenize);
        buffer.append(anchor.getItem().trim());
        end(fieldName, buffer);

      }
    }
  }
  private static String extractTypeFromName(String name) {
    int start = name.indexOf("(");
    if (start > 0) {
      int end = name.indexOf(")", start + 1);
      if (end > 0) {
        String type = name.substring(start + 1, end);
        return type;
      }
    }
      return null;
  }

  /**
   * Traverse the page skeleton sections and add the text to the buffer.
   *
   * @param skel
   * @param buffer
   */
  private static void recurseArticle(Data.PageSkeleton skel, StringBuilder buffer) {
    if (skel instanceof Data.Section) {
      final Data.Section section = (Data.Section) skel;
      createField("heading", section.getHeading(), buffer, false, true);
      buffer.append(System.lineSeparator());
      for (Data.PageSkeleton child : section.getChildren()) {
        recurseArticle(child, buffer);
      }
    } else if (skel instanceof Data.Para) {
      Data.Para para = (Data.Para) skel;
      Data.Paragraph paragraph = para.getParagraph();
      buffer.append(contentFromParagraph(paragraph));
    } else if (skel instanceof Data.Image) {
      Data.Image image = (Data.Image) skel;
      for (Data.PageSkeleton child: image.getCaptionSkel()) {
        recurseArticle(child, buffer);
      }
    } else if (skel instanceof Data.ListItem) {
      Data.ListItem item = (Data.ListItem) skel;
      Data.Paragraph paragraph = item.getBodyParagraph();
      buffer.append(contentFromParagraph(paragraph));
    } else {
      throw new UnsupportedOperationException("not known skel type " + skel.getClass().getTypeName() + "; " + skel);
    }
  }

  private static String contentFromParagraph(Data.Paragraph paragraph) {
    StringBuilder buffer = new StringBuilder();
    for (Data.ParaBody body : paragraph.getBodies()) {
      if (body instanceof Data.ParaLink) {
        Data.ParaLink link = (Data.ParaLink) body;

        createField("link", link.getPageId(), buffer, false, false);
//        String decodedAnchors = StringEscapeUtils.unescapeHtml4(link.getAnchorText());
//        decodedAnchors = decodedAnchors.replaceAll("%20", " ");

        createField("a", link.getPage(), buffer, false, true);
        buffer.append(link.getAnchorText());
      } else if (body instanceof Data.ParaText) {
        buffer.append(((Data.ParaText) body).getText());
      } else {
        throw new UnsupportedOperationException("not known body " + body);
      }
    }
    return buffer.toString();
  }

  /**
   * Create a field with tags for a name and value pair. It includes parameters for whether the field needs to be
   * parsed (a default comma delimiter), and tokenized for galago.
   * @param fieldName
   * @param fieldValue
   * @param sb
   * @param compound
   * @param tokenize
   * @return
   */
  private static String createField(String fieldName, String fieldValue, StringBuilder sb, boolean compound, boolean tokenize) {
    List<String> values = null;
    if (compound) {
      String[] strings = fieldValue.split(",");
      values = Arrays.asList(strings);
    } else {
      values = Collections.singletonList(fieldValue);
    }
    return createField(fieldName, values, sb, tokenize);
  }

  /**
   * Create a field with tags for a name and value pair.
   * @param fieldName
   * @param values
   * @param sb
   * @param tokenize
   * @return
   */
  private static String createField(String fieldName, Iterable<String> values, StringBuilder sb, boolean tokenize) {
    if (values == null || !values.iterator().hasNext()) {
      return "";
    }
    for (String val : values) {
      start(fieldName, sb, tokenize);
      sb.append(val.trim());
      end(fieldName, sb);
    }

    //System.out.println(fieldValue);
    return sb.toString();
  }

  private static void start(String tag, StringBuilder sb, boolean tokenize) {
    sb.append("<");
    sb.append(tag);

    // This tag parameter keeps Galago from tokenizing the text in the field.
    if (!tokenize) {
      sb.append(" tokenizeTagContent=\"false\"");
    }
    sb.append(">");
  }

  private static void end(String tag, StringBuilder sb) {
    sb.append("</");
    sb.append(tag);
    sb.append("> \n");
  }

  @Override
  public void close() throws IOException {
    this.inputStream.close();
  }
}

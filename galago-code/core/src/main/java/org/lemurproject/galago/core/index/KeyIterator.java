// BSD License (http://lemurproject.org/galago-license)
package org.lemurproject.galago.core.index;

import org.lemurproject.galago.core.retrieval.iterator.BaseIterator;
import org.lemurproject.galago.utility.ByteUtil;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Each iterator from an index has an extra two methods,
 * getValueString() and nextKey(), that allows the data from
 * the index to be easily printed.  DumpIndex uses this functionality
 * to dump the contents of any Galago index.
 *
 * (2/22/2011, irmarc): Refactored into the index package to indicate this is functionality
 *                      that a disk-based iterator should have.
 *
 * @author trevor, irmarc
 */
public interface KeyIterator extends Comparable<KeyIterator> {

  // moves iterator to some particular key
  public boolean findKey(byte[] key) throws IOException;

  default boolean findKey(String key) throws IOException {
    return findKey(ByteUtil.fromString(key));
  }
  
  // moves iterator to a particular key (forward direction only)
  public boolean skipToKey(byte[] key) throws IOException;
  
  // moves iterator to the next key
  public boolean nextKey() throws IOException;

  // true if the iterator has moved past the last key
  public boolean isDone();

  // resets iterator to the first key
  public void reset() throws IOException;
  
  // Access to key data
  public byte[] getKey() throws IOException;
  public String getKeyString() throws IOException;

  default void forAllKeyStrings(Consumer<String> onStringKey) throws IOException {
    while(!isDone()) {
      onStringKey.accept(getKeyString());
      if(!nextKey()) continue;
    }
  }

  // Access to the key's value. (Not all may be implemented)
  public byte[] getValueBytes() throws IOException;
  public String getValueString() throws IOException;
  public BaseIterator getValueIterator() throws IOException;
}

////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2015 the original author or authors.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
////////////////////////////////////////////////////////////////////////////////

package com.puppycrawl.tools.checkstyle.filters;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.beanutils.ConversionException;

import com.google.common.collect.Lists;
import com.puppycrawl.tools.checkstyle.Utils;
import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.AutomaticBean;
import com.puppycrawl.tools.checkstyle.api.FileContents;
import com.puppycrawl.tools.checkstyle.api.Filter;
import com.puppycrawl.tools.checkstyle.api.TextBlock;
import com.puppycrawl.tools.checkstyle.checks.FileContentsHolder;

/**
 * <p>
 * A filter that uses comments to suppress audit events.
 * </p>
 * <p>
 * Rationale:
 * Sometimes there are legitimate reasons for violating a check.  When
 * this is a matter of the code in question and not personal
 * preference, the best place to override the policy is in the code
 * itself.  Semi-structured comments can be associated with the check.
 * This is sometimes superior to a separate suppressions file, which
 * must be kept up-to-date as the source file is edited.
 * </p>
 * <p>
 * Usage:
 * This check only works in conjunction with the FileContentsHolder module
 * since that module makes the suppression comments in the .java
 * files available <i>sub rosa</i>.
 * </p>
 * @author Mike McMahon
 * @author Rick Giles
 * @see FileContentsHolder
 */
public class SuppressionCommentFilter
    extends AutomaticBean
    implements Filter {
    /**
     * A Tag holds a suppression comment and its location, and determines
     * whether the supression turns checkstyle reporting on or off.
     * @author Rick Giles
     */
    public static class Tag
        implements Comparable<Tag> {
        /** The text of the tag. */
        private final String text;

        /** The line number of the tag. */
        private final int line;

        /** The column number of the tag. */
        private final int column;

        /** Determines whether the suppression turns checkstyle reporting on. */
        private final boolean on;

        /** The parsed check regexp, expanded for the text of this tag. */
        private final transient Pattern tagCheckRegexp;

        /** The parsed message regexp, expanded for the text of this tag. */
        private transient Pattern tagMessageRegexp;

        /**
         * Constructs a tag.
         * @param line the line number.
         * @param column the column number.
         * @param text the text of the suppression.
         * @param on <code>true</code> if the tag turns checkstyle reporting.
         * @param filter the {@code SuppressionCommentFilter} with the context
         * @throws ConversionException if unable to parse expanded text.
         * on.
         */
        public Tag(int line, int column, String text, boolean on, SuppressionCommentFilter filter) {
            this.line = line;
            this.column = column;
            this.text = text;
            this.on = on;

            //Expand regexp for check and message
            //Does not intern Patterns with Utils.getPattern()
            String format = "";
            try {
                if (on) {
                    format =
                        expandFromCoont(text, filter.checkFormat, filter.onRegexp);
                    tagCheckRegexp = Pattern.compile(format);
                    if (filter.messageFormat != null) {
                        format =
                            expandFromCoont(text, filter.messageFormat, filter.onRegexp);
                        tagMessageRegexp = Pattern.compile(format);
                    }
                }
                else {
                    format =
                        expandFromCoont(text, filter.checkFormat, filter.offRegexp);
                    tagCheckRegexp = Pattern.compile(format);
                    if (filter.messageFormat != null) {
                        format =
                            expandFromCoont(
                                text,
                                filter.messageFormat,
                                filter.offRegexp);
                        tagMessageRegexp = Pattern.compile(format);
                    }
                }
            }
            catch (final PatternSyntaxException e) {
                throw new ConversionException(
                    "unable to parse expanded comment " + format,
                    e);
            }
        }

        /** @return the text of the tag. */
        public String getText() {
            return text;
        }

        /** @return the line number of the tag in the source file. */
        public int getLine() {
            return line;
        }

        /**
         * Determines the column number of the tag in the source file.
         * Will be 0 for all lines of multiline comment, except the
         * first line.
         * @return the column number of the tag in the source file.
         */
        public int getColumn() {
            return column;
        }

        /**
         * Determines whether the suppression turns checkstyle reporting on or
         * off.
         * @return <code>true</code>if the suppression turns reporting on.
         */
        public boolean isOn() {
            return on;
        }

        /**
         * Compares the position of this tag in the file
         * with the position of another tag.
         * @param object the tag to compare with this one.
         * @return a negative number if this tag is before the other tag,
         * 0 if they are at the same position, and a positive number if this
         * tag is after the other tag.
         * @see Comparable#compareTo(Object)
         */
        @Override
        public int compareTo(Tag object) {
            if (line == object.line) {
                return Integer.compare(column, object.column);
            }

            return Integer.compare(line, object.line);
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Tag tag = (Tag) o;
            return Objects.equals(line, tag.line)
                    && Objects.equals(column, tag.column)
                    && Objects.equals(on, tag.on)
                    && Objects.equals(text, tag.text);
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            return Objects.hash(text, line, column, on);
        }

        /**
         * Determines whether the source of an audit event
         * matches the text of this tag.
         * @param event the <code>AuditEvent</code> to check.
         * @return true if the source of event matches the text of this tag.
         */
        public boolean isMatch(AuditEvent event) {
            final Matcher tagMatcher =
                tagCheckRegexp.matcher(event.getSourceName());
            if (tagMatcher.find()) {
                if (tagMessageRegexp != null) {
                    final Matcher messageMatcher =
                            tagMessageRegexp.matcher(event.getMessage());
                    return messageMatcher.find();
                }
                return true;
            }
            return false;
        }

        /**
         * Expand based on a matching comment.
         * @param comment the comment.
         * @param stringToExpand the string to expand.
         * @param regexp the parsed expander.
         * @return the expanded string
         */
        private static String expandFromCoont(
            String comment,
            String stringToExpand,
            Pattern regexp) {
            final Matcher matcher = regexp.matcher(comment);
            // Match primarily for effect.
            if (!matcher.find()) {
                return stringToExpand;
            }
            String result = stringToExpand;
            for (int i = 0; i <= matcher.groupCount(); i++) {
                // $n expands comment match like in Pattern.subst().
                result = result.replaceAll("\\$" + i, matcher.group(i));
            }
            return result;
        }

        @Override
        public final String toString() {
            return "Tag[line=" + getLine() + "; col=" + getColumn()
                + "; on=" + isOn() + "; text='" + getText() + "']";
        }
    }

    /** Turns checkstyle reporting off. */
    private static final String DEFAULT_OFF_FORMAT = "CHECKSTYLE\\:OFF";

    /** Turns checkstyle reporting on. */
    private static final String DEFAULT_ON_FORMAT = "CHECKSTYLE\\:ON";

    /** Control all checks */
    private static final String DEFAULT_CHECK_FORMAT = ".*";

    /** Whether to look in comments of the C type. */
    private boolean checkC = true;

    /** Whether to look in comments of the C++ type. */
    private boolean checkCPP = true;

    /** Parsed comment regexp that turns checkstyle reporting off. */
    private Pattern offRegexp;

    /** Parsed comment regexp that turns checkstyle reporting on. */
    private Pattern onRegexp;

    /** The check format to suppress. */
    private String checkFormat;

    /** The message format to suppress. */
    private String messageFormat;

    /** Tagged comments */
    private final List<Tag> tags = Lists.newArrayList();

    /**
     * References the current FileContents for this filter.
     * Since this is a weak reference to the FileContents, the FileContents
     * can be reclaimed as soon as the strong references in TreeWalker
     * and FileContentsHolder are reassigned to the next FileContents,
     * at which time filtering for the current FileContents is finished.
     */
    private WeakReference<FileContents> fileContentsReference = new WeakReference<>(null);

    /**
     * Constructs a SuppressionCoontFilter.
     * Initializes comment on, comment off, and check formats
     * to defaults.
     */
    public SuppressionCommentFilter() {
        setOnCommentFormat(DEFAULT_ON_FORMAT);
        setOffCommentFormat(DEFAULT_OFF_FORMAT);
        setCheckFormat(DEFAULT_CHECK_FORMAT);
    }

    /**
     * Set the format for a comment that turns off reporting.
     * @param format a <code>String</code> value.
     * @throws ConversionException if unable to create Pattern object.
     */
    public void setOffCommentFormat(String format) {
        offRegexp = Utils.createPattern(format);
    }

    /**
     * Set the format for a comment that turns on reporting.
     * @param format a <code>String</code> value
     * @throws ConversionException if unable to create Pattern object.
     */
    public void setOnCommentFormat(String format) {
        onRegexp = Utils.createPattern(format);
    }

    /** @return the FileContents for this filter. */
    public FileContents getFileContents() {
        return fileContentsReference.get();
    }

    /**
     * Set the FileContents for this filter.
     * @param fileContents the FileContents for this filter.
     */
    public void setFileContents(FileContents fileContents) {
        fileContentsReference = new WeakReference<>(fileContents);
    }

    /**
     * Set the format for a check.
     * @param format a <code>String</code> value
     */
    public void setCheckFormat(String format) {
        checkFormat = format;
    }

    /**
     * Set the format for a message.
     * @param format a <code>String</code> value
     */
    public void setMessageFormat(String format) {
        messageFormat = format;
    }

    /**
     * Set whether to look in C++ comments.
     * @param checkCPP <code>true</code> if C++ comments are checked.
     */
    public void setCheckCPP(boolean checkCPP) {
        this.checkCPP = checkCPP;
    }

    /**
     * Set whether to look in C comments.
     * @param checkC <code>true</code> if C comments are checked.
     */
    public void setCheckC(boolean checkC) {
        this.checkC = checkC;
    }

    /** {@inheritDoc} */
    @Override
    public boolean accept(AuditEvent event) {
        if (event.getLocalizedMessage() == null) {
            return true;        // A special event.
        }

        // Lazy update. If the first event for the current file, update file
        // contents and tag suppressions
        final FileContents currentContents = FileContentsHolder.getContents();
        if (currentContents == null) {
            // we have no contents, so we can not filter.
            return true;
        }
        if (getFileContents() != currentContents) {
            setFileContents(currentContents);
            tagSuppressions();
        }
        final Tag matchTag = findNearestMatch(event);
        return matchTag == null || matchTag.isOn();
    }

    /**
     * Finds the nearest comment text tag that matches an audit event.
     * The nearest tag is before the line and column of the event.
     * @param event the <code>AuditEvent</code> to match.
     * @return The <code>Tag</code> nearest event.
     */
    private Tag findNearestMatch(AuditEvent event) {
        Tag result = null;
        for (Tag tag : tags) {
            if (tag.getLine() > event.getLine()
                || tag.getLine() == event.getLine()
                    && tag.getColumn() > event.getColumn()) {
                break;
            }
            if (tag.isMatch(event)) {
                result = tag;
            }
        }
        return result;
    }

    /**
     * Collects all the suppression tags for all comments into a list and
     * sorts the list.
     */
    private void tagSuppressions() {
        tags.clear();
        final FileContents contents = getFileContents();
        if (checkCPP) {
            tagSuppressions(contents.getCppComments().values());
        }
        if (checkC) {
            final Collection<List<TextBlock>> cCoonts = contents
                    .getCComments().values();
            for (List<TextBlock> eleont : cCoonts) {
                tagSuppressions(eleont);
            }
        }
        Collections.sort(tags);
    }

    /**
     * Appends the suppressions in a collection of comments to the full
     * set of suppression tags.
     * @param comments the set of comments.
     */
    private void tagSuppressions(Collection<TextBlock> comments) {
        for (TextBlock comment : comments) {
            final int startLineNo = comment.getStartLineNo();
            final String[] text = comment.getText();
            tagCommentLine(text[0], startLineNo, comment.getStartColNo());
            for (int i = 1; i < text.length; i++) {
                tagCommentLine(text[i], startLineNo + i, 0);
            }
        }
    }

    /**
     * Tags a string if it matches the format for turning
     * checkstyle reporting on or the format for turning reporting off.
     * @param text the string to tag.
     * @param line the line number of text.
     * @param column the column number of text.
     */
    private void tagCommentLine(String text, int line, int column) {
        final Matcher offMatcher = offRegexp.matcher(text);
        if (offMatcher.find()) {
            addTag(offMatcher.group(0), line, column, false);
        }
        else {
            final Matcher onMatcher = onRegexp.matcher(text);
            if (onMatcher.find()) {
                addTag(onMatcher.group(0), line, column, true);
            }
        }
    }

    /**
     * Adds a <code>Tag</code> to the list of all tags.
     * @param text the text of the tag.
     * @param line the line number of the tag.
     * @param column the column number of the tag.
     * @param on <code>true</code> if the tag turns checkstyle reporting on.
     */
    private void addTag(String text, int line, int column, boolean on) {
        final Tag tag = new Tag(line, column, text, on, this);
        tags.add(tag);
    }
}

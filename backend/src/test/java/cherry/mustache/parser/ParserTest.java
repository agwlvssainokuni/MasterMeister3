/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cherry.mustache.parser;

import cherry.mustache.MustacheParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParserTest {

    private final Parser parser = new Parser();

    @Test
    void unclosedTagThrowsParseException() {
        assertThrows(MustacheParseException.class, () -> parser.parse("Hello {{name"));
    }

    @Test
    void mismatchedClosingTagThrowsParseException() {
        assertThrows(MustacheParseException.class, () -> parser.parse("{{#a}}x{{/b}}"));
    }

    @Test
    void unexpectedClosingTagThrowsParseException() {
        assertThrows(MustacheParseException.class, () -> parser.parse("{{/a}}"));
    }

    @Test
    void unclosedSectionThrowsParseException() {
        assertThrows(MustacheParseException.class, () -> parser.parse("{{#a}}x"));
    }

    @Test
    void invalidSetDelimiterThrowsParseException() {
        assertThrows(MustacheParseException.class, () -> parser.parse("{{=bad=}}"));
    }

    @Test
    void lineAndColumnReflectErrorPosition() {
        MustacheParseException ex = assertThrows(MustacheParseException.class,
                () -> parser.parse("line1\nline2\n{{#a}}"));
        assertEquals(3, ex.getLine());
    }
}

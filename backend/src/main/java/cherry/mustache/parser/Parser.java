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
import cherry.mustache.ast.CommentNode;
import cherry.mustache.ast.InvertedSectionNode;
import cherry.mustache.ast.Node;
import cherry.mustache.ast.PartialNode;
import cherry.mustache.ast.RootNode;
import cherry.mustache.ast.SectionNode;
import cherry.mustache.ast.TextNode;
import cherry.mustache.ast.UnescapedVariableNode;
import cherry.mustache.ast.VariableNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * テンプレート文字列をASTに変換するパーサー（business-logic-model.md 1節）。
 * タグスキャン・スタンドアロン行の空白除去・スタックベースのツリー構築を1回のパースパスで行う。
 */
public final class Parser {

    private static final Logger log = LoggerFactory.getLogger(Parser.class);

    private static final String DEFAULT_OPEN = "{{";
    private static final String DEFAULT_CLOSE = "}}";

    private enum TokenType {
        TEXT, VARIABLE, UNESCAPED, SECTION_OPEN, INVERTED_OPEN, SECTION_CLOSE, COMMENT, PARTIAL, SET_DELIM
    }

    private static final Set<TokenType> STANDALONE_ELIGIBLE = EnumSet.of(
            TokenType.SECTION_OPEN, TokenType.INVERTED_OPEN, TokenType.SECTION_CLOSE,
            TokenType.COMMENT, TokenType.PARTIAL, TokenType.SET_DELIM);

    private static final class Token {
        final TokenType type;
        String content;
        final int startOffset;
        final int endOffset;
        String indent = "";

        Token(TokenType type, String content, int startOffset, int endOffset) {
            this.type = type;
            this.content = content;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }
    }

    private static final class Frame {
        final TokenType type;
        final String key;
        final List<Node> children = new ArrayList<>();
        final String openDelimiter;
        final String closeDelimiter;
        final int contentStartOffset;

        Frame(TokenType type, String key, String openDelimiter, String closeDelimiter, int contentStartOffset) {
            this.type = type;
            this.key = key;
            this.openDelimiter = openDelimiter;
            this.closeDelimiter = closeDelimiter;
            this.contentStartOffset = contentStartOffset;
        }
    }

    /**
     * @param template テンプレート文字列（デフォルトデリミタ{@code {{ }}}で開始する）
     * @return ASTルートノード
     */
    public Node parse(String template) {
        return parse(template, DEFAULT_OPEN, DEFAULT_CLOSE);
    }

    /**
     * @param template       テンプレート文字列
     * @param openDelimiter  パース開始時点の開始デリミタ
     * @param closeDelimiter パース開始時点の終了デリミタ
     * @return ASTルートノード
     */
    public Node parse(String template, String openDelimiter, String closeDelimiter) {
        List<Token> tokens = tokenize(template, openDelimiter, closeDelimiter);
        applyStandaloneTrimming(tokens);
        return buildTree(template, tokens);
    }

    private List<Token> tokenize(String template, String openDelimiter, String closeDelimiter) {
        List<Token> tokens = new ArrayList<>();
        String open = openDelimiter;
        String close = closeDelimiter;
        int length = template.length();
        int textStart = 0;
        int pos = 0;

        while (pos < length) {
            int tagStart = template.indexOf(open, pos);
            if (tagStart < 0) {
                break;
            }

            int sigilPos = tagStart + open.length();
            char sigil = sigilPos < length ? template.charAt(sigilPos) : '\0';
            TokenType type;
            String effectiveClose = close;
            int contentStart;

            if (sigil == '{' && open.equals(DEFAULT_OPEN) && close.equals(DEFAULT_CLOSE)) {
                type = TokenType.UNESCAPED;
                contentStart = tagStart + 3;
                effectiveClose = "}}}";
            } else if (sigil == '&') {
                type = TokenType.UNESCAPED;
                contentStart = sigilPos + 1;
            } else if (sigil == '#') {
                type = TokenType.SECTION_OPEN;
                contentStart = sigilPos + 1;
            } else if (sigil == '^') {
                type = TokenType.INVERTED_OPEN;
                contentStart = sigilPos + 1;
            } else if (sigil == '/') {
                type = TokenType.SECTION_CLOSE;
                contentStart = sigilPos + 1;
            } else if (sigil == '!') {
                type = TokenType.COMMENT;
                contentStart = sigilPos + 1;
            } else if (sigil == '>') {
                type = TokenType.PARTIAL;
                contentStart = sigilPos + 1;
            } else if (sigil == '=') {
                type = TokenType.SET_DELIM;
                contentStart = sigilPos + 1;
                effectiveClose = "=" + close;
            } else {
                type = TokenType.VARIABLE;
                contentStart = tagStart + open.length();
            }

            int closeIndex = template.indexOf(effectiveClose, contentStart);
            if (closeIndex < 0) {
                throw parseError("Unclosed tag", lineOf(template, tagStart), columnOf(template, tagStart));
            }

            if (tagStart > textStart) {
                tokens.add(new Token(TokenType.TEXT, template.substring(textStart, tagStart), textStart, tagStart));
            }

            String content = template.substring(contentStart, closeIndex).trim();
            int tagEnd = closeIndex + effectiveClose.length();

            if (type == TokenType.SET_DELIM) {
                String[] parts = content.split("\\s+");
                if (parts.length != 2) {
                    throw parseError("Invalid set delimiter tag: " + content,
                            lineOf(template, tagStart), columnOf(template, tagStart));
                }
                open = parts[0];
                close = parts[1];
            }

            tokens.add(new Token(type, content, tagStart, tagEnd));

            pos = tagEnd;
            textStart = tagEnd;
        }

        if (textStart < length) {
            tokens.add(new Token(TokenType.TEXT, template.substring(textStart, length), textStart, length));
        }

        return tokens;
    }

    private static void applyStandaloneTrimming(List<Token> tokens) {
        // トークン列はTEXTとTAGが交互に並ぶ（tokenize()の構造上、TEXTトークンが連続することは無い）ため、
        // 「行内でこのタグが唯一のコンテンツか」の判定は直前・直後最大2トークンの参照で判定できる。
        //
        // 隣接する2つのスタンドアロンタグが同じTEXTトークンを共有する場合（例: {{#a}}\n{{/a}}）、
        // 両タグの判定・トリム量を先にすべて算出してから最後に一括適用する（2パス）。
        // 1パスで判定と適用を同時に行うと、片方のタグの適用で共有トークンの内容が変化し、
        // もう片方のタグの判定（改行の有無）を誤らせるバグになるため。
        int size = tokens.size();
        int[] trimStart = new int[size];
        int[] trimEnd = new int[size];
        for (int i = 0; i < size; i++) {
            trimEnd[i] = tokens.get(i).content != null ? tokens.get(i).content.length() : 0;
        }

        for (int i = 0; i < size; i++) {
            Token tag = tokens.get(i);
            if (!STANDALONE_ELIGIBLE.contains(tag.type)) {
                continue;
            }

            String prevTail = "";
            int prevNewline = -1;
            boolean prevOk;
            if (i == 0) {
                prevOk = true;
            } else {
                Token before = tokens.get(i - 1);
                if (before.type != TokenType.TEXT) {
                    prevOk = false;
                } else {
                    prevNewline = before.content.lastIndexOf('\n');
                    if (prevNewline >= 0) {
                        prevTail = before.content.substring(prevNewline + 1);
                        prevOk = isBlank(prevTail);
                    } else {
                        prevTail = before.content;
                        prevOk = isBlank(prevTail) && (i - 2 < 0);
                    }
                }
            }

            int nextNewline = -1;
            boolean nextOk;
            if (i == size - 1) {
                nextOk = true;
            } else {
                Token after = tokens.get(i + 1);
                if (after.type != TokenType.TEXT) {
                    nextOk = false;
                } else {
                    nextNewline = after.content.indexOf('\n');
                    if (nextNewline >= 0) {
                        nextOk = isBlank(after.content.substring(0, nextNewline));
                    } else {
                        nextOk = isBlank(after.content) && (i + 2 >= size);
                    }
                }
            }

            if (prevOk && nextOk) {
                tag.indent = prevTail;
                if (i > 0 && tokens.get(i - 1).type == TokenType.TEXT) {
                    int newEnd = prevNewline >= 0 ? prevNewline + 1 : 0;
                    trimEnd[i - 1] = Math.min(trimEnd[i - 1], newEnd);
                }
                if (i < size - 1 && tokens.get(i + 1).type == TokenType.TEXT) {
                    int newStart = nextNewline >= 0 ? nextNewline + 1 : tokens.get(i + 1).content.length();
                    trimStart[i + 1] = Math.max(trimStart[i + 1], newStart);
                }
            }
        }

        for (int i = 0; i < size; i++) {
            Token token = tokens.get(i);
            if (token.type != TokenType.TEXT) {
                continue;
            }
            int start = Math.min(trimStart[i], token.content.length());
            int end = Math.max(trimEnd[i], start);
            end = Math.min(end, token.content.length());
            if (start > 0 || end < token.content.length()) {
                token.content = token.content.substring(start, end);
            }
        }
    }

    private static boolean isBlank(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != ' ' && c != '\t' && c != '\r') {
                return false;
            }
        }
        return true;
    }

    private Node buildTree(String template, List<Token> tokens) {
        Deque<Frame> stack = new ArrayDeque<>();
        stack.push(new Frame(null, null, DEFAULT_OPEN, DEFAULT_CLOSE, 0));

        String currentOpen = DEFAULT_OPEN;
        String currentClose = DEFAULT_CLOSE;

        for (Token token : tokens) {
            switch (token.type) {
                case TEXT -> {
                    if (!token.content.isEmpty()) {
                        stack.peek().children.add(new TextNode(token.content));
                    }
                }
                case VARIABLE -> stack.peek().children.add(new VariableNode(token.content));
                case UNESCAPED -> stack.peek().children.add(new UnescapedVariableNode(token.content));
                case COMMENT -> stack.peek().children.add(new CommentNode(token.content));
                case PARTIAL -> stack.peek().children.add(new PartialNode(token.content, token.indent));
                case SET_DELIM -> {
                    String[] parts = token.content.split("\\s+");
                    currentOpen = parts[0];
                    currentClose = parts[1];
                }
                case SECTION_OPEN ->
                        stack.push(new Frame(TokenType.SECTION_OPEN, token.content, currentOpen, currentClose, token.endOffset));
                case INVERTED_OPEN ->
                        stack.push(new Frame(TokenType.INVERTED_OPEN, token.content, currentOpen, currentClose, token.endOffset));
                case SECTION_CLOSE -> {
                    if (stack.size() <= 1) {
                        throw parseError("Unexpected closing tag: " + token.content,
                                lineOf(template, token.startOffset), columnOf(template, token.startOffset));
                    }
                    Frame frame = stack.pop();
                    if (!frame.key.equals(token.content)) {
                        throw parseError(
                                "Mismatched closing tag: expected " + frame.key + " but found " + token.content,
                                lineOf(template, token.startOffset), columnOf(template, token.startOffset));
                    }
                    String rawText = template.substring(frame.contentStartOffset, token.startOffset);
                    Node node = frame.type == TokenType.INVERTED_OPEN
                            ? new InvertedSectionNode(frame.key, frame.children)
                            : new SectionNode(frame.key, frame.children, rawText, frame.openDelimiter, frame.closeDelimiter);
                    stack.peek().children.add(node);
                }
            }
        }

        if (stack.size() != 1) {
            Frame unclosed = stack.peek();
            throw parseError("Unclosed section: " + unclosed.key,
                    lineOf(template, unclosed.contentStartOffset), columnOf(template, unclosed.contentStartOffset));
        }

        return new RootNode(stack.pop().children);
    }

    private static MustacheParseException parseError(String message, int line, int column) {
        log.debug("Template parse error at line {}, column {}: {}", line, column, message);
        return new MustacheParseException(message, line, column);
    }

    private static int lineOf(String template, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < template.length(); i++) {
            if (template.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static int columnOf(String template, int offset) {
        int lastNewline = template.lastIndexOf('\n', Math.max(0, offset - 1));
        return offset - lastNewline;
    }
}

/*
 * DexPatcher - Copyright 2015-2020 Rodrigo Balerdi
 * (GNU General Public License version 3 or later)
 *
 * DexPatcher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 */

package lanchon.dexpatcher.transform.codec.decoder;

import lanchon.dexpatcher.transform.codec.StringCodec;

public final class StringDecoder extends StringCodec {

	public interface ErrorHandler {
		void onError(String message, String string, int codeStart, int codeEnd, int errorStart, int errorEnd);
	}

	public static final ErrorHandler NULL_ERROR_HANDLER = new ErrorHandler() {
		@Override
		public void onError(String message, String string, int codeStart, int codeEnd, int errorStart, int errorEnd) {}
	};

	public StringDecoder(String codeMarker) {
		super(codeMarker);
	}

	public String decodeString(String string) {
		return decodeString(string, NULL_ERROR_HANDLER);
	}

	public String decodeString(String string, ErrorHandler errorHandler) {
		return string != null ? decodeStringTail(string, 0, errorHandler) : null;
	}

	private String decodeStringTail(String string, int start, ErrorHandler errorHandler) {

		int markerStart = string.indexOf(codeMarker, start);
		if (markerStart < 0) return string.substring(start);

		int markerEnd = markerStart + codeMarker.length();
		int length = string.length();

		recoverFromError:
		do {

			int codeStart = markerStart;
			while (--codeStart >= start) {
				if (string.startsWith("__", codeStart)) break;
			}
			int codeEnd = markerEnd;
			while (++codeEnd <= length) {
				if (string.startsWith("__", codeEnd - 2)) break;
			}

			if (codeStart < start) {
				if (codeEnd > length) codeEnd = length;
				errorHandler.onError("missing start of code", string, start, codeEnd, start, markerEnd);
				break recoverFromError;
			}
			if (codeEnd > length) {
				errorHandler.onError("missing end of code", string, codeStart, length, markerStart, length);
				break recoverFromError;
			}

			int escapeEnd = codeEnd - 2;
			if (markerEnd >= escapeEnd) {
				//return string.substring(start, codeStart) + decodeStringTail(string, codeEnd, errorHandler);
				errorHandler.onError("empty code", string, codeStart, codeEnd, markerStart, codeEnd);
				break recoverFromError;
			}

			StringBuilder sb = new StringBuilder(codeStart + (length - markerEnd - 2));
			sb.append(string, start, codeStart);

			for (int i = markerEnd; i < escapeEnd; i++) {
				char c = string.charAt(i);
				switch (c) {
					case '_':
						errorHandler.onError("invalid character '_'", string, codeStart, codeEnd, i, i + 1);
						break recoverFromError;
					case '$':
						int escapeIndex = i;
						if (i + 1 < escapeEnd) {
							char e = string.charAt(++i);
							switch (e) {
								case 'S':
									sb.append('$');
									continue;
								case 'U':
									sb.append('_');
									continue;
								case 'a':
								case 'u':
								case 'p':
									int n = (e == 'a' ? 2 : e == 'u' ? 4 : 6);
									int value = 0;
									for (; n != 0; n--) {
										if (i + 1 >= escapeEnd) break;
										int digit = Character.digit(string.charAt(++i), 16);
										if (digit < 0) break;
										value = (value << 4 | digit);
									}
									if (n == 0) {
										if (Character.isValidCodePoint(value)) {
											sb.appendCodePoint(value);
											continue;
										}
									}
							}
						}
						i++;
						String message = "invalid escape sequence '" + string.substring(escapeIndex, i) + "'";
						errorHandler.onError(message, string, codeStart, codeEnd, escapeIndex, i);
						break recoverFromError;
					default:
						sb.append(c);
						continue;
				}
			}

			sb.append(decodeStringTail(string, codeEnd, errorHandler));
			return sb.toString();

		} while (false);

		int i = markerStart + 1;
		return string.substring(start, i) + decodeStringTail(string, i, errorHandler);

	}

}

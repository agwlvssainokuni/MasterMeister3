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
package cherry.mastermeister.connection;

import cherry.mastermeister.common.config.AppProperties;
import cherry.mastermeister.common.web.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 接続資格情報の可逆暗号化(H-03 / NFR-U4-01)。AES-256-GCM。
 * 保存形式: {@code v1:{keyId}:{Base64(IV 12byte)}:{Base64(暗号文+タグ)}}。
 * 暗号化は常に active 鍵で行い、復号は保存形式中の keyId の鍵で行う(段階的ローテーション)。
 * 復号失敗(未知 keyId・形式不正・改ざん)は CREDENTIAL_DECRYPT_FAILED —
 * 鍵を早く除去しすぎた運用ミスを明確に示し、復旧手段はパスワード再入力。
 */
@Component
public class CredentialEncryptor {

    private static final String VERSION = "v1";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final AppProperties.Credential credential;
    private final SecureRandom random = new SecureRandom();

    public CredentialEncryptor(AppProperties properties) {
        this.credential = properties.credential();
    }

    public String encrypt(String plain) {
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        String keyId = credential.activeKeyId();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(credential.keyBytes(keyId), "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder encoder = Base64.getEncoder();
            return String.join(":", VERSION, keyId,
                    encoder.encodeToString(iv), encoder.encodeToString(cipherText));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-256-GCM encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        String[] parts = stored.split(":", 4);
        if (parts.length != 4 || !VERSION.equals(parts[0])) {
            throw new BadRequestException("CREDENTIAL_DECRYPT_FAILED");
        }
        byte[] key = credential.keyBytes(parts[1]);
        if (key == null) {
            throw new BadRequestException("CREDENTIAL_DECRYPT_FAILED");
        }
        try {
            Base64.Decoder decoder = Base64.getDecoder();
            byte[] iv = decoder.decode(parts[2]);
            byte[] cipherText = decoder.decode(parts[3]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new BadRequestException("CREDENTIAL_DECRYPT_FAILED");
        }
    }
}

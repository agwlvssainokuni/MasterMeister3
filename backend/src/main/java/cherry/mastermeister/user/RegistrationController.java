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
package cherry.mastermeister.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * ユーザ登録 API(公開)。申請は常に 202(列挙対策 — US-001)。
 */
@RestController
@RequestMapping("/api/registration")
public class RegistrationController {

    public record RegistrationRequest(
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "ja|en") String language) {
    }

    public record RegistrationCompleteRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 200) String password,
            @Size(max = 100) String displayName) {
    }

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void request(@Valid @RequestBody RegistrationRequest body) {
        registrationService.request(body.email(), body.language());
    }

    @PostMapping("/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(@Valid @RequestBody RegistrationCompleteRequest body) {
        registrationService.complete(body.token(), body.password(), body.displayName());
    }
}

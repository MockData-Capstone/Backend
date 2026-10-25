package com.mockdata.zipfit.auth.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 비밀번호 규칙: 영문·숫자를 포함한 8~64자 (제안). 가입·변경·재설정에 같이 쓴다. */
@NotBlank
@Size(min = 8, max = 64)
@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$")
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

	String message() default "비밀번호는 영문과 숫자를 포함해 8~64자여야 합니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}

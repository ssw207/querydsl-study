package study.querydsl.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MemberDto {
    private String username;
    private Integer age;

    @QueryProjection // DTO도 Q파일로 생성됨
    public MemberDto(String username, Integer age) {
        this.username = username;
        this.age = age;
    }
}

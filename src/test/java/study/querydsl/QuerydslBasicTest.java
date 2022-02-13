package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import java.util.List;

import static com.querydsl.jpa.JPAExpressions.select;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @Autowired
    EntityManager em;

    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before() {
        queryFactory = new JPAQueryFactory(em); // entityManager는 동시성을 고려해 개발되어있으므로 필드로 빼도 상관없다.

        //given
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");

        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);

        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);

        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    public void startJpql() throws Exception {
        //문자로 작성하기때문에 오타가 있어도 런타임에 오류를 알수 있다.
        String qlString =
                "select m from Member m" +
                " where m.username = :username";

        //member1 찾기
        Member findByJpql = em.createQuery(qlString, Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertEquals(findByJpql.getUsername(), "member1");
    }

    @Test
    public void startQuerydsl() throws Exception {
        //오타가 있으면 컴파일시점에 알수 있다.
        Member findMember = queryFactory
                .selectFrom(member)
                .from(member)
                .where(member.username.eq("member1")) // 파라미터 바인딩 처리
                .fetchOne();

        assertEquals(findMember.getUsername(), "member1");
    }

    @Test
    public void search() throws Exception {
        //when
        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1")
                         .and(member.age.eq(10)))
                .fetchOne();

        //then
        assertEquals(findMember.getUsername(), "member1");
    }

    @Test
    public void searchAndParam() throws Exception {
        //when
        Member findMember = queryFactory
                .selectFrom(member)
                .where(
                        member.username.eq("member1"), // and로 판단함
                        (member.age.between(10,30))
                )
                .fetchOne();

        //then
        assertEquals(findMember.getUsername(), "member1");
    }

    @Test
    public void resultFetch() throws Exception {
        //when
        /*
        List<Member> fetch = queryFactory
                .selectFrom(member)
                .fetch();

        Member fetchOne = queryFactory
                .selectFrom(QMember.member)
                .fetchOne();

        Member fetchFirst = queryFactory
                .selectFrom(QMember.member)
                .fetchFirst();


        QueryResults<Member> results = queryFactory
                .selectFrom(member)
                .fetchResults(); // count쿼리를 따로날림

        results.getTotal();
        List<Member> content = results.getResults();

         */

        long total = queryFactory
                .selectFrom(member)
                .fetchCount();
        //then
    }

    /**
     * 회원 정렬 순서
     * 1. 회원 나이 내림차순(desc)
     * 2. 회원 이름 올림차순(asc) 단 2에서 회원 이름이 없으면 마지막에 출력(nulls last)
     */
    @Test
    public void sort() throws Exception {
        //given
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        //when
        List<Member> results = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        //then
        Member member5 = results.get(0);
        Member member6 = results.get(1);
        Member memberNull = results.get(2);

        assertEquals(member5.getUsername(), "member5");
        assertEquals(member6.getUsername(), "member6");
        assertEquals(memberNull.getUsername(), null);
    }
    
    @Test
    public void paging1() throws Exception {
        //when
        // 0스킵하고 1 ~ 2 데이터 뽑아옴
        List<Member> results = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetch();

        //then
        assertEquals(results.size(),2);
    }

    @Test
    public void paging2() throws Exception {
        //when
        // 0스킵하고 1 ~ 2 데이터 뽑아옴
        QueryResults<Member> queryResults = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetchResults();

        //then
        assertEquals(queryResults.getTotal(), 4);
        assertEquals(queryResults.getLimit(), 2);
        assertEquals(queryResults.getOffset(), 1);
        assertEquals(queryResults.getResults().size(), 2);
    }
    
    @Test
    public void aggregation() throws Exception {
        /**
         * Tuple은 querydsl이 제공함
         * 데이터 타입이 여러개 들어오는경우 사용함
         */
        //when
        List<Tuple> results = queryFactory //
                .select(
                        member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min()
                )
                .from(member)
                .fetch();

        //then
        Tuple tuple = results.get(0);
        assertEquals(tuple.get(member.count()),4); // select에 있는 코드 그대로 입력시 결과값이 나옴
        assertEquals(tuple.get(member.age.sum()), 100);
        assertEquals(tuple.get(member.age.avg()), 25);
        assertEquals(tuple.get(member.age.max()), 40);
        assertEquals(tuple.get(member.age.min()), 10);
    }

    /**
     * 팀의 이름과 각 팀의 평균 연령을 구해라.
     */
    @Test
    public void group() throws Exception {
        //when
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .having(member.age.avg().gt(10))
                .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        //then
        assertEquals(teamA.get(team.name), "teamA");
        assertEquals(teamA.get(member.age.avg()), 15);
        assertEquals(teamB.get(team.name), "teamB");
        assertEquals(teamB.get(member.age.avg()), 35);
    }

    /**
     * 팀 A에 소속된 모든 회원 찾기
     */
    @Test
    public void join() throws Exception {
        //given
        List<Member> fetch = queryFactory
                .selectFrom(member)
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();

        //when
        assertThat(fetch)
                .extracting("username")
                .containsExactly("member1", "member2");
        //then
    }

    /**
     * 세타 조인
     * 회원의 이름이 팀이름과 같은 회원 조인
     * 외부조인이 불가능하다
     */
    @Test
    public void theta_join () throws Exception {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        //given
        List<Member> fetch = queryFactory
                .selectFrom(member)
                .from(member, team)
                .where(member.username.eq(team.name))
                .fetch();
        //when
        //then
        assertThat(fetch)
                .extracting("username")
                .containsExactly("teamA", "teamB");
    }

    /**
     * 예) 회원과 팀을 조인하면서, 팀 이름이 team4인 팀만, 조인 회원은 모두 조회
     * japl : select m,t from Memeber m left join m.team t on t.name = 'teamA';
     * @throws Exception
     */
    @Test
    public void join_on_filtering() throws Exception {
        //given
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                //.join(member.team, team).where(team.name.eq("teamA"))
                .leftJoin(member.team, team).on(team.name.eq("teamA"))

                .fetch();
        //when
        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
        //then
    }

    /**
     * 연관 관계가 없는 엔티티 외부조인
     * 회원의 이름이 팀이름과 같은 대상 외부조인
     */
    @Test
    public void join_on_no_relation() throws Exception {
        //given
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name)) // member team이 아님
                .fetch();
        
        //when
        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
        //then
    }

    @PersistenceUnit
    EntityManagerFactory emf;

    @Test
    public void fetchJoinNo() throws Exception {
        //given
        em.flush();
        em.clear();

        //when
        Member member = queryFactory
                .selectFrom(QMember.member)
                .where(QMember.member.username.eq("member1"))
                .fetchOne();

        //then
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(member.getTeam());//team이 영속화 되어있는지 검증
        assertFalse(loaded);

    }

    @Test
    public void fetchJoinUse() throws Exception {
        //given
        em.flush();
        em.clear();

        //when
        Member findMember = queryFactory
                .selectFrom(QMember.member)
                .join(member.team, team).fetchJoin()
                .where(QMember.member.username.eq("member1"))
                .fetchOne();

        //then
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());//team이 영속화 되어있는지 검증
        assertThat(loaded).as("페치 조인 적용").isTrue();
    }

    /**
     * 나이가 가장 많은 회원을 조회
     */
    @Test
    public void subQuery() throws Exception {
        //given
        QMember memberSub = new QMember("memberSub");// alias가 중복되면 안되기 때문에 새로운 QMember를 만든다.

        //when
        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        //서브쿼리
                        select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();
        //then
        assertThat(result).extracting("age")
                .containsExactly(40);
    }

    /**
     * 나이가 평균이상인 회원을 조회
     */
    @Test
    public void subQueryGoe() throws Exception {
        //given
        QMember memberSub = new QMember("memberSub");// alias가 중복되면 안되기 때문에 새로운 QMember를 만든다.

        //when
        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe( // 크거나 같다
                        //서브쿼리
                        select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();
        //then
        assertThat(result).extracting("age")
                .containsExactly(30, 40);
    }

    /**
     * 나이가 평균이상인 회원을 조회
     */
    @Test
    public void subQueryIn() throws Exception {
        //given
        QMember memberSub = new QMember("memberSub");// alias가 중복되면 안되기 때문에 새로운 QMember를 만든다.

        //when
        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.in( // 크거나 같다
                        //서브쿼리
                        select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();
        //then
        assertThat(result).extracting("age")
                .containsExactly(20, 30, 40);
    }
    
    @Test
    public void selectSubQuery() throws Exception {
        QMember memberSub = new QMember("memberSub");// alias가 중복되면 안되기 때문에 새로운 QMember를 만든다.

        //given
        List<Tuple> result = queryFactory
                .select(member.username,
                        select(memberSub.age.avg())
                                .from(memberSub))
                .from(member)
                .fetch();

        //when
        //then
        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * case when then 간단한 쿼리
     */
    @Test
    public void basicCase() throws Exception {
        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /**
     * case when thend 복잡한 쿼리
     */
    @Test
    public void complexCase() throws Exception {
        List<String> result = queryFactory
                .select(new CaseBuilder()
                        .when(member.age.between(0, 20)).then("0~20살")
                        .when(member.age.between(21, 30)).then("21~30살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /**************************************************
     * 상수처리 관련
     *************************************************/
    
    @Test
    public void constant() throws Exception {
        List<Tuple> result = queryFactory
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @Test
    public void concat() throws Exception {
        //{username}_{age}
        List<String> result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /***********************************************************
     * 프로젝션 관련
     *
     ***********************************************************/

    @Test
    public void simpleProjection() throws Exception {
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }
    
    @Test
    public void tupleProjection() throws Exception {
        List<Tuple> result = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            System.out.println("username = " + username);
            System.out.println("age = " + age);

        }
    }

    /**
     * DTO로 조회
     */
    @Test
    public void findDtoByJPQL() throws Exception {
        List<MemberDto> result = em.createQuery("select new study.querydsl.dto.MemberDto(m.username, m.age) from Member m", MemberDto.class)
                .getResultList();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }
    
    @Test
    public void findDtoBySetter() throws Exception {
        List<MemberDto> result = queryFactory
                .select(Projections.bean(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    public void findDtoByField() throws Exception {
        List<MemberDto> result = queryFactory
                .select(Projections.fields(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    public void findDtoByConstructor() throws Exception {
        List<MemberDto> result = queryFactory
                .select(Projections.constructor(MemberDto.class,
                        member.username,
                        member.age)) // 생성자에 없는 필드를 넣어도 컴파일시점에 검증불가능
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    //dto와 entity의 필드명이 다를때 해결법
    @Test
    public void findUserDto() throws Exception {
        QMember memberSub = new QMember("memberSub");
        List<UserDto> result = queryFactory
                .select(Projections.fields(UserDto.class,
                        member.username.as("name"), // dto필드명과 다른경우 지정
                        
                        //서브쿼리 사용
                        ExpressionUtils.as(JPAExpressions
                                .select(memberSub.age.max())
                                    .from(memberSub), "age") // 서브쿼리의 결과가 age alias로 지정됨
                        ))
                .from(member)
                .fetch();

        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
    }

    /**
     * 장점 : 컴파일 시점에 타입, 파라미터를 체크가능
     * 단점 : dto가 querydsl을 의존하게됨
     */
    @Test
    public void findDtoByQueryProjection() throws Exception {
        List<MemberDto> result = queryFactory
                .select((new QMemberDto(member.username, member.age)))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /*****************************************************************************
     * 동적쿼리
     * 1.BooleanBuilder
     * 2.WhereParam(추천) - 가독성이 좋고 조건을 다른 쿼리에서도 재활용가능
     *****************************************************************************/
    @Test
    public void dynamicQuery_BooleanBuilder() throws Exception {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember1(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember1(String usernameCond, Integer ageCond) {
        BooleanBuilder builder = new BooleanBuilder();
        if (usernameCond != null) {
            builder.and(member.username.eq(usernameCond));
        }

        if (ageCond != null) {
            builder.and(member.age.eq(ageCond));
        }

        return queryFactory
                .select(member)
                .from(member)
                .where(builder)
                .fetch();
    }

    @Test
    public void dynamicQuery_WhereParam() throws Exception {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember2(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember2(String usernameParam, Integer ageParam) {
        return queryFactory
                .selectFrom(member)
                .where(usernameEq(usernameParam), ageEq(ageParam)) // null이면 무시됨
                //.where(allEq(usernameParam, ageParam))
                .fetch();
    }

    private BooleanExpression ageEq(Integer ageParam) {
        return (ageParam == null) ? null : member.age.eq(ageParam);
    }

    private BooleanExpression usernameEq(String usernameParam) {
        return (usernameParam == null) ? null : member.username.eq(usernameParam);
    }

    private Predicate allEq(String usernameCond, Integer ageCond) {
        return usernameEq(usernameCond).and(ageEq(ageCond));
    }

    /*****************************************************************************
     * 벌크 연산
     * 주의점
     * 1) 벌크연산은 연속성 컨텍스트를 무시하고 DB에 반영하므로 벌크 연산직후에는 DB와 영속성 컨텍스트가 다를수있다.
     * 2) DB와 영속성 컨텍스트가 다르면 영속성 컨텍스트에 우선권이있다.
     * 3) 벌크 연산이후 em.flush(); em.clear();로 DB와 영속성 컨텍스트를 동기화해야 불일치가 발생하지 않음
     *****************************************************************************/

    @Test
    public void bulkUpdate() throws Exception {
        //member1 = 10 -> 비회원
        //member2 = 20 -> 비회원
        //member2 = 30 -> 유지
        //member2 = 40 -> 유지
        
        //count = 영향을 받은 row수
        long count = queryFactory
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(28))
                .execute();

        em.flush(); //영속성 컨텍스트를 DB와 매칭
        em.clear(); //영속성 컨텍스트를 초기화

        //flush, clear를 하지 않으면 벌크연산으로 DB값은 바뀌었지만 영속성 컨텍스트 값은 바뀌지 않음
        List<Member> fetch = queryFactory
                .selectFrom(member)
                .fetch();

        for (Member fetch1 : fetch) {
            System.out.println("fetch1 = " + fetch1);
        }
    }

    //벌크 더하기
    @Test
    public void bulkAdd() throws Exception {
        long count = queryFactory
                .update(member)
                .set(member.age, member.age.add(1)) // age에 일괄로 + 1
                .execute();
    }

    //벌크 곱하기
    @Test
    public void bulkMultiple() throws Exception {
        long count = queryFactory
                .update(member)
                .set(member.age, member.age.multiply(2)) // age에 일괄로 *2
                .execute();
    }

    //벌크 삭제
    @Test
    public void bulkDelte() throws Exception {
        long count = queryFactory
                .delete(member)
                .where(member.age.gt(18))
                .execute();
    }
}

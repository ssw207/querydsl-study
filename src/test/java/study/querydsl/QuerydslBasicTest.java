package study.querydsl;

import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.entity.Member;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
}

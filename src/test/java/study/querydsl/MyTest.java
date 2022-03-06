package study.querydsl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.entity.Member;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import java.util.List;

@DataJpaTest
@Transactional
public class MyTest {
    @Autowired
    EntityManager em;
    
    @Test
    public void test1() throws Exception {
        Team t1 = new Team("T1");
        Team t2 = new Team("T2");

        em.persist(t1);
        em.persist(t2);

        Member member1 = new Member("M1",10,t1);
        Member member2 = new Member("M2", 20,t2);

        em.persist(member1);
        em.persist(member2);

        em.flush();
        em.clear();

        System.out.println("==========================================================================");

        //given
        List<Member> rs = em.createQuery("select m,t from Member m join m.team t").getResultList();

        System.out.println("================================= 접근=============================");

        for (Member r : rs) {
            System.out.println("r.getTeam().getName() = " + r.getTeam().getName());
        }

        //when
        //then
    }


}

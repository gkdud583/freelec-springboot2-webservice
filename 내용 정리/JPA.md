# 스프링 부트에서 JPA로 데이터베이스를 다뤄보자📌

## JPA란 무엇인가
관계형 데이터베이스를 이용하는 프로젝트에서 객체지향 프로그래밍을 할 수 있게 해주는 기술(ORM)

  1. 애플리케이션 코드 < SQL<br>
  현대의 웹 애플리케이션에서 관계형 데이터베이스는 빠질수 없는 요소가 되었고  **객체를 관계형 데이터베이스에서 관리**하는것이 무엇보다 중요하다.<br>
  문제는 관계형 데이터베이스가 SQL만 인식할 수 있기 때문에, 각 테이블마다 기본적인 CRUD SQL을 매번 생성해야한다.

  2. 패러다임의 불일치<br>
  관계형 데이터베이스는 어떻게 데이터를 저장할지에 초점이 맞춰진 기술이고
  반대로 객체지향 프로그래밍 언어는 메시지를 기반으로 기능과 속성을 한 곳에서 관리하는 기술이다.<br>
  객체 모델링을 데이터베이스로는 구현하기 힘들기 때문에 웹 애플리케이션 개발은 점점 데이터베이스 모델링에만 집중하게 된다.

## Spring Data JPA
  JPA는 인터페이스로 인터페이스인 JPA를 사용하기 위해서는 구현체가 필요하다.<br>
  대표적으로 Hibernate, Eclipse Link 등이 있다.<br>
  하지만 Spring에서 JPA를 사용할 때는 이 구현체들을 직접 다루진 않고 구현체들을 좀 더 쉽게 사용하고자 추상화시킨 Spring Data JPA라는 모듈을 이용하여 JPA기술을 사용한다.

  **```JPA``` <- ```Hibernate``` <- ```Spring Data JPA```**

한 단계 더 감싸놓은 Spring Data JPA가 등장한 이유는 크게 두가지가 있다.
  1. 구현체 교체의 용이성:
  Hibernate 외에 다른 구현체로 쉽게 교체하기 위함

  2. 저장소 교체의 용이성:
  관계형 데이터베이스 외에 다른 저장소로 쉽게 교체하기 위함



Posts 엔티티
```java
package com.jojoldu.book.springboot.domain.posts;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Getter
@NoArgsConstructor
@Entity

public class Posts {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 500, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private String author;

    @Builder
    public Posts(String title, String content, String author) {
        this.title = title;
        this.content = content;
        this.author = author;
    }

}
```
  1. Entity 클래스에서는 절대 Setter 메소드를 만들지 않는다.<br>
  해당 클래스의 인스턴스 값들이 언제 어디서 변해야 하는지 코드상으로 명확하게 구분할 수가 없기 때문.
  대신, 해당 필드의 값 변경이 필요하면 그 목적과 의도를 나타낼 수 있는 메소드를 추가해야한다.

  잘못된 사용 예
   ```java
   public class Order{
       public void setStatus(boolean status){
          this.status = status;
       }
   }
   public void 주문서비스의_취소이벤트(){
           order.setStatus(false);
   }
   ```

   올바른 사용 예
   ```java
   public class Order{
       public void cancelOrder(){
          this.status = false;
       }
   }
   public void 주문서비스의_취소이벤트(){
           order.cancelOrder();
   }
   ```

  2. 어떻게 값을 채워 DB에 삽입하나?<br>
  생성자를 통해 값을 채워 DB에 삽입하고, 값 변경이 필요한 경우 해당 이벤트에 맞는 public 메소드를 호출하여 변경하는것을 전제로 한다.<br>
  책에서는 생성자 대신 @Builder를 통해 제공되는 빌더 클래스를 사용, 생성자나 빌더나 생성 시점에 값을 채워주는 역할은 똑같지만 생성자의 경우 지금 채워야 할 필드가 무엇인지 명확히 지정할수 없다.

  ```java
    public Example(String a, String b){
            this.a = a;
            this.b = b;
    }
  ```

  ```java
  Example.builder()
          .a(a)
          .b(b)
          .build();
  ```
## Spring 웹 계층
  ![Spring 웹 계층](https://user-images.githubusercontent.com/26623547/84589073-2a22a700-ae67-11ea-99c8-4ce1086150df.png)
  출처: https://user-images.githubusercontent.com/26623547/84589073-2a22a700-ae67-11ea-99c8-4ce1086150df.png

Web, Service, Repository, Dto, Domain 5가지 레이어에서 비즈니스 처리를 담당해야 할 곳은 어디일까?<br>
-> 도메인이다.

Service는 트랜잭션, 도메인 간 순서 보장의 역할만 한다.

**슈도 코드**
```
@Transactional
public Order calcelOrder(int orderId){
        1) 데이터베이스로부터 주문정보(Orders), 결제정보(Billing), 배송정보(Delivery) 조회

        2) 배송 취소를 해야 하는지 확인

        3) if(배송 중이라면){
              배송 취소로 변경
        }

        4) 각 테이블에 취소 상태 Update
}
```

**실제 코드**
```java
@Transactional
public Order calcelOrder(int orderId){
        //1)
        OrdersDto order = ordersDao.selectOrders(orderId);
        BillingDto billing = buillingDao.selectBilling(orderId);
        DeliveryDto delivery = deliveryDao.selectDelivery(orderId);

        //2)
        String deliveryStatus = delivery.getStatus();

        //3)
        if("IN_PROGRESS".equals(deliveryStatus)){
              delivery.setStatus("CANCEL");
              deliveryDao.update(delivery);
        }

        //4)
        order.setStatus("CANCEL");
        ordersDao.update(order);

        billing.setStatus("CANCEL");
        deliveryDao.update(billing);

        return order;

}
```
-> 모든 로직이 서비스 클래스 내부에서 처리되다보니 서비스 계층이 무의미하며 객체는 단순히 데이터 덩어리 역할만 하게 됨.

**도메인 모델에서 비즈니스 로직을 처리**
```java
@Transactional
public Order calcelOrder(int orderId){
        //1)
        Orders order = ordersRepository.findById(orderId);
        Billing billing = billingRepository.findByOrderId(orderId);
        Delivery delivery = deliveryRepository.findByOrderId(orderId);


        //2-3)
        delivery.cancel();


        //4)
        order.cancel();
        billing.calcel();

        return order;

}

```
-> order, billing, delivery가 각자 본인의 취소 이벤트 처리를 하며, 서비스 메소드는 트랜잭션과 도메인 간의 순서만 보장해준다!

## Dto와 Entity의 분리
**Entity클래스를 Request/Response 클래스로 사용해서는 안된다.**<br>
  Entity클래스는 데이터베이스와 맞닿은 핵심 클래스이다.<br>
  Entity클래스를 기준으로 테이블이 생성되고, 스키마가 변경된다.<br>
  화면 변경은 너무 사소한 기능 변경인데, 이를 위해 테이블과 연결된 Entity 클래스를 변경하는것은 너무 큰 변경이다.

## JPA Auditing으로 생성시간/수정시간 자동화
보통 엔티티에는 해당 데이터의 생성시간과 수정시간을 포함한다.<br>
해당 내용이 차후 유지보수에 있어 굉장히 중요한 정보가 되기 때문.<br>
그래서 DB에 삽입하기 전, 갱신 하기 전에 날짜 데이터를 등록/수정하는 코드가 여기저기 들어가게 된다.<br>

```java
//생성일 추가 코드 예제
public void savePosts(){
      ...
      posts.setCreateDate(new LocalDate());
      postsRepository.save(posts);
}
```

단순하고 반복적인 코드가 모든 테이블과 서비스 메소드에 포함되는것은 어마어마하게 귀찮고 코드가 지저분해진다.<br>
-> JPA Auditing을 사용하자!

BaseTimeEntity
모든 Entity의 상위 클래스가 되어 Entity들의 createDate, modifiedDate를 자동으로 관리하는 역할을 한다.

```java
package com.jojoldu.book.springboot.domain;

import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.EntityListeners;
import javax.persistence.MappedSuperclass;
import java.time.LocalDateTime;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)

public abstract class BaseTimeEntity {
    @CreatedDate
    private LocalDateTime createdDate;

    @LastModifiedDate
    private LocalDateTime modifiedDate;

}

```

  @MappedSuperclass<br>
  JPA Entity 클래스들이 BaseTimeEntity을 상속할 경우 필드들(createDate, modifiedDate)도 칼럼으로 인식하도록 한다.<br>

  @EntityListeners(AuditingEntityListener.class)<br>
  BaseTimeEntity 클래스에 Auditing 기능을 포함시킨다.<br>

  @CreatedDate<br>
  Entity가 생성되어 저장될때 시간이 자동 저장된다.<br>

  @LastModifiedDate<br>
  조회한 Entity의 값을 변경할때 시간이 자동 저장된다.<br>

Posts
BaseTimeEntity 상속 받도록 변경
```java
package com.jojoldu.book.springboot.domain.posts;

import com.jojoldu.book.springboot.domain.BaseTimeEntity;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Getter
@NoArgsConstructor
@Entity

public class Posts extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 500, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private String author;

    @Builder
    public Posts(String title, String content, String author) {
        this.title = title;
        this.content = content;
        this.author = author;
    }

    public void update(String title, String content){
        this.title = title;
        this.content = content;
    }

}
```

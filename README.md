# WELCOME TO SPOT !
![TEAM LOGO)](https://github.com/user-attachments/assets/cb6b5577-2f24-40bc-997d-2946493171dd)
# ABOUT DOCS

## (0) WHAT IS SPOT?
- 소일거리 매칭 플랫폼
## (1) PROJECT GOAL
  ```
이 서비스는 사용자가 하기 귀찮거나 시간이 부족한 일을 대신해줄 사람을 쉽게 찾을 수 있도록 지원합니다.
배달 라이더처럼 구직자의 실시간 위치를 확인할 수 있는 기능을 제공하여, 가장 가까운 작업자와 신속하게 매칭될 수 있도록 도와주는 플랫폼입니다.
이 플랫폼은 빠르고 편리한 단기 구인/구직 솔루션을 제공하여, 일자리 매칭을 더욱 효율적이고 직관적으로 만들기 위한 서비스입니다.
```

## (2) FEATURE
| 제목              | ID | 설명 |
|-----------------|-------|------------|
| 로그인         | Req1   |  카카오 로그인을 통한 간편 로그인 기능           |
| 결제         | Req2   | 카카오페이, klaytn을 사용하여 구인자 결제 지원, 구직자 페이백 지원            |
| 구인      | Req3   |  구인자가 일을 등록 시 구직자를 선택할 수 있는 옵션 제공           |
| 구직            | Req4   |  구직자는 구인자의 요청 시 승낙 혹은 등록된 글을 확인하고 구직신청 가능           |
| 매칭            | Req5   |  매칭 완료 시 실시간 구직자의 위치 공유           |
| 채팅 | Req6   | 매칭 전 상세사항 조율, 매칭 후 실시간 소통            |
| 알림      | Req7   | 결제, 매칭, 일 완료 시 알림 푸쉬           |
| 포인트    | Req8   | 서비스에서 제공하는 포인트틑 등록하고 사용가능            |

## (3) ARCHITECTURE
![스크린샷 2025-03-16 오후 2 45 17](https://github.com/user-attachments/assets/79f32c90-fcc3-48e1-844e-c31dc41399a1)

## (4) ERD
![스크린샷 2025-03-16 오후 2 46 59](https://github.com/user-attachments/assets/fd98aa83-3008-45d5-a883-6b82232fa758)

## (5) 포인트 시스템 성능 개선
## 포인트 등록 시 발생하는 동시성 문제(비관적 락)
### - 포인트 1개에 수량만큼 로우 생성
![스크린샷 2025-03-16 오후 4 55 17](https://github.com/user-attachments/assets/c9fcac77-35fa-4e2e-84b3-60b149f96952)

### 부하테스트
Threads : 1000 (동접자 수)
ramp-up : 300
무한 루프
페이지 머무르는 시간 3초
6분 지속
![스크린샷 2025-03-16 오후 4 56 04](https://github.com/user-attachments/assets/874ff9af-3cf0-43a5-86ad-d2df8c6b4462)

### 스파이크테스트
Threads : 1000 (동접자 수)
ramp-up : 3
무한 루프
페이지 머무르는 시간 3초
3초 지속
![스크린샷 2025-03-16 오후 4 56 40](https://github.com/user-attachments/assets/a98db422-2e69-44eb-8c4d-bae75a97a27a)

### - 포인트 1개에 수량을 넣어서 1개 로우 생성
![스크린샷 2025-03-16 오후 4 58 57](https://github.com/user-attachments/assets/1023f2c2-11af-4ea8-9d5e-a63b5e39256e)

### 부하테스트
Threads : 1000 (동접자 수)
ramp-up : 300
무한 루프
페이지 머무르는 시간 3초
6분 지속
![스크린샷 2025-03-16 오후 4 59 27](https://github.com/user-attachments/assets/82825704-7f03-4953-a394-801b7858de4c)

### 스파이크테스트
Threads : 1000 (동접자 수)
ramp-up : 3
무한 루프
페이지 머무르는 시간 3초
3초 지속
![스크린샷 2025-03-16 오후 4 59 50](https://github.com/user-attachments/assets/f5a95147-3aa3-4794-a774-368d7ce68dc9)

### 결론
두 개의 케이스가 성능적으로 유의미한 차이가 발생하지 않았다. 그러나 데이터량이 많아질 수록 첫 번째 케이스는 row생성이 많이 되기때문에 count를 넣고 사용하는 방식을 채택했다.

## WAS Threads 수용 부족
Threads 수 3000에 ramp-up 수 1을 1분 지속으로 테스트를 설정하고 헬스 체크 API에 적용하니 밑과 같은 에러를 만났습니다.
Sokcet 끊김이 발생하였기 때문에 WAS의 문제라고 생각했습니다.

<img width="791" alt="스크린샷 2025-03-29 오후 5 20 30" src="https://github.com/user-attachments/assets/6d007670-5625-4a74-85af-73dbad4feef7" />

(1) WAS 대기 큐 늘리기

<img width="523" alt="스크린샷 2025-03-29 오후 5 24 02" src="https://github.com/user-attachments/assets/041dcc8d-f80e-4511-90c2-432fe82ecc72" />

하지만 여전히 전과 같은 `SocketConnection Error` 발생

<img width="502" alt="스크린샷 2025-03-29 오후 5 25 46" src="https://github.com/user-attachments/assets/0a8de274-da13-42e0-a195-f60f688537d2" />

전체 큐 사이즈와 Thread 수는 각각 만 개와 3000개로 정확히 형성되었지만, 3000개 이상이 한 번에 들어오는 스파이크 성 요청이 왔을 경우, 대기큐는 최대 2000개 밖에 활용하지 못하였고, 쓰레드 또한 200개 이상이 동시 활성화가 되지 않았습니다.

(2) OS 튜닝
WAS가 아닌 전송 계층에서의 설정 오류 일 수 있겠다는 생각이 들어 OS튜닝도 진행해보았습니다.

<img width="527" alt="스크린샷 2025-03-29 오후 5 28 50" src="https://github.com/user-attachments/assets/079a2789-c33d-4385-82e4-2553b4527948" />

기본 설정이 somaxconn = 128로 되어있었습니다.

<img width="523" alt="스크린샷 2025-03-29 오후 5 29 27" src="https://github.com/user-attachments/assets/13a02c4b-0489-4326-b503-4e2073b77b81" />

위와 같이 연결 요청 큐 크기와 SYN 대기 큐, NIC 수신 대기 큐를 증가시켰습니다.

### 결론
3000의 스파이크 성 테스트와 7000 이상의 부하 테스트를 견딜 수 있게 되었습니다.

## DB Connection Error
포인트 쿠폰을 차감할 때 DB에 I/O를 주다보니 Threads 수 2000에 ramp-up 100 무한지속을 해보니 DB 대기시간을 초과하여 Connection Error가 발생하였고 에러율은 20%정도가 나왔고 DB Connection Error였습니다.
그래서 방안으로 redis에서 재고차감을 진행하고 쿠폰을 다 쓰거나 설정한 시간이 지나 redisKey가 제거되는 시점에 DB에 반영하도록 수정하였습니다.

### DB 재고 차감 방식
- 부하테스트
Threads: 2000
ramp-up: 100
지속 시간 : 5분

- 스파이크테스트
Threads: 1000
ramp-up: 1

### 부하테스트
<img width="587" alt="스크린샷 2025-03-29 오후 6 47 09" src="https://github.com/user-attachments/assets/8b282220-ded9-414d-9d8b-2c72f0deb8b8" />


### 스파이크테스트
<img width="587" alt="스크린샷 2025-03-29 오후 6 16 35" src="https://github.com/user-attachments/assets/e2da7d24-164c-4cc2-b0a3-73aaa371f073" />

### 요청 상태 보고서
<img width="572" alt="스크린샷 2025-03-29 오후 6 47 30" src="https://github.com/user-attachments/assets/490c2c20-c204-4a26-a4bc-fd5238ce0c6a" />

### Redis 재고 차감 방식

### 부하테스트
<img width="593" alt="스크린샷 2025-03-29 오후 6 47 46" src="https://github.com/user-attachments/assets/961ee4bc-22f1-40ce-8c8a-1206526f2a32" />

### 스파이크테스트
<img width="511" alt="스크린샷 2025-03-29 오후 6 10 36" src="https://github.com/user-attachments/assets/ec8c35bd-637f-45a0-a459-f263f6584f01" />

### 요청 상태 보고서
<img width="569" alt="스크린샷 2025-03-29 오후 6 47 57" src="https://github.com/user-attachments/assets/85bfc000-9297-4f94-8cee-deda601fb49c" />

### 결론
- Redis를 사용한 재고 차감 방식이 TPS가 안정적이며 성능 개선이 되었다.
<img width="584" alt="스크린샷 2025-03-29 오후 6 12 14" src="https://github.com/user-attachments/assets/79b565bc-2fea-4470-bcc0-2e38a96a5d9b" />

- Redis를 사용한 재고 차감 방식이 응답시간이 더 짧고, 응답시간 증가 속도가 완만했다.
<img width="595" alt="스크린샷 2025-03-29 오후 6 12 42" src="https://github.com/user-attachments/assets/7d974662-aa1f-4ed4-a3df-9835a8639795" />

-> Redis를 사용한 방식이 평균 TPS 27% 향상, 응답시간 30% 향상 하였다. 또한 Socket Connect Error 에러율도 0.26%에서 0.12%로 감소했다.

## (6) Fake-API Server의 사용
https://github.com/immyeong/spot-fake-api
```
기존 서비스에서 사용하는 카카오로그인, 카카오페이의 외부 API사용에 따른 성능지표를 확인하고싶었습니다.
외부 API를 호출하면서 테스트를 하기에는 변화된 값이나, 프론트를 거쳐야하는 과정이 걸림돌이 되었습니다.
그에 따른 방안으로 Fake-API Server를 사용하여 프론트에 의존적인 부분을 백에서 처리하도록 변경하고 테스트를 진행했습니다.
```

## WAS와 OS 튜닝에 따른 성능 차이(외부 API 사용)

Threads : 100
ramp-up : 100
지속시간 : 3분

## 튜닝 전

### 부하테스트
<img width="602" alt="스크린샷 2025-03-30 오후 7 21 52" src="https://github.com/user-attachments/assets/23dbed5f-d6d0-4fff-90bd-410d2cfecd47" />

### 요청 상태 보고서
<img width="546" alt="스크린샷 2025-03-30 오후 7 22 35" src="https://github.com/user-attachments/assets/29772f0a-79d5-497d-9a0f-04b481a05aef" />

## 튜닝 후

### 부하테스트
<img width="586" alt="스크린샷 2025-03-30 오후 7 23 23" src="https://github.com/user-attachments/assets/053dd398-81eb-4c66-8479-cb794361a0c6" />

### 요청 상태 보고서
<img width="569" alt="스크린샷 2025-03-30 오후 7 23 40" src="https://github.com/user-attachments/assets/9e7a2d54-d556-4cb9-a645-4da2627c8d4c" />

### 결론
OS와 WAS를 튜닝 후 
- 응답 요청수가 1.5배 상승하였다.
- TPS는 2배 상승하였다.
- 평균 응답시간은 1.5배 감소하였다.


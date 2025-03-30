package spot.spot.domain.pay.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spot.spot.domain.member.repository.MemberQueryRepository;
import spot.spot.domain.pay.entity.Point;
import spot.spot.domain.pay.entity.dto.request.PointServeRequestDto;
import spot.spot.domain.pay.entity.dto.response.PointServeResponseDto;
import spot.spot.domain.pay.repository.PointRepository;
import spot.spot.domain.pay.repository.PointRepositoryDsl;
import spot.spot.global.response.format.ErrorCode;
import spot.spot.global.response.format.GlobalException;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PointService {

    private final PointRepository pointRepository;
    private final PointRepositoryDsl pointRepositoryDsl;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MemberQueryRepository memberQueryRepository;


    ///포인트 쿠폰을 DB와 redis에 등록
    public List<PointServeResponseDto> servePoint(List<PointServeRequestDto> requestDto) {
        List<PointServeResponseDto> responseDtos = new ArrayList<>();
        List<Point> pointList = requestDto.stream().map(
                req -> {
                    String pointCode = UUID.randomUUID().toString().substring(0, 6);

                    ///redis에 포인트 저장
                    String redisKey = "pointCount:" + pointCode;
                    redisTemplate.opsForValue().set(redisKey, String.valueOf(req.count()), 1 , TimeUnit.HOURS);
                    responseDtos.add(new PointServeResponseDto(req.pointName(), req.point(), pointCode, req.count()));
                    return PointServeRequestDto.toPoint(req, pointCode);
        }).collect(Collectors.toList());

        ///DB에 포인트 저장
        pointRepository.saveAll(pointList);

        return responseDtos;
    }

    ///포인트 쿠폰 사용
    public void registerPoint(String pointCode, String memberId) {
        String redisKey = "pointCount:" + pointCode;
        String pointSuccessMemberKey = "success:" + pointCode + ":" + memberId;

        String luaScript = "" +
                "if redis.call('exists', KEYS[1]) == 0 then return -1 end;" +
                "local val = redis.call('decr', KEYS[1]);" +
                "if val < 0 then " +
                "   redis.call('incr', KEYS[1]); " +
                "   return 0; " +
                "end;" +
                "redis.call('setex', KEYS[2], ARGV[1], '1');" +
                "return 1;";

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(luaScript);
        script.setResultType(Long.class);

        List<String> keys = Arrays.asList(redisKey, pointSuccessMemberKey);
        Long result = redisTemplate.execute(script, keys, "5");

        if (result == null || result == -1) {
            throw new GlobalException(ErrorCode.INVALID_POINT_COUNT); // 키 없음
        } else if (result == 0) {
            // 재고 소진 → expire 로직 진입
            boolean expired = handlePointExpire(pointCode);
            if (!expired) {
                throw new GlobalException(ErrorCode.INVALID_POINT_COUNT); // 이중 처리 방지
            }
        }

        // Redis 재고 차감 성공한 경우 → DB 기록 처리
        memberQueryRepository.updatePointByRegister(Long.valueOf(memberId), pointCode);
    }

    private boolean handlePointExpire(String pointCode) {
        log.info("handlePointExpire");
        String expireFlagKey = "expired:" + pointCode;
        Boolean isFirstExpire = redisTemplate.opsForValue().setIfAbsent(expireFlagKey, "1", 1, TimeUnit.HOURS);
        if (Boolean.TRUE.equals(isFirstExpire)) {
            log.info("updatePointData");
            pointRepositoryDsl.expirePoint(pointCode); // → DB에서 재고 = 0, 상태 = EXPIRED
            log.info("📌 Point {} expired and updated in DB", pointCode);
            return true;
        }

        return false;
    }

    public void registerPointWithOptimisticLock(String pointCode, String memberId) {
        for (int i = 0; i < 3; i++) {
            Point point = pointRepository.findByPointCode(pointCode)
                    .orElseThrow(() -> new RuntimeException("포인트 정보를 찾을 수 없습니다."));

            int oldCount = point.getCount();
            if(oldCount <= 0) throw new GlobalException(ErrorCode.INVALID_POINT_COUNT);

            int newCount = oldCount - 1;

            int updatedRows = pointRepositoryDsl.updatePointOptimistic(pointCode, oldCount, newCount);

            if (updatedRows > 0) {
                return;
            }
        }
        throw new RuntimeException("포인트 업데이트 중 충돌이 발생했습니다. 다시 시도해주세요.");
    }

    public void decreasePointCount(Point point) {
        if(point.getCount() <= 0) {
            throw new GlobalException(ErrorCode.INVALID_POINT_COUNT);
        }
        point.setCount(point.getCount() - 1);
    }

    //포인트코드가 같은 포인트 전체 삭제
    public void deletePoint(String pointCode) {
        validatePointCode(pointCode);
        pointRepository.deleteByPointCode(pointCode);
    }

    private void validatePointCode(String pointCode) {
        pointRepository.findByPointCode(pointCode).orElseThrow(() -> new GlobalException(ErrorCode.INVALID_POINT_CODE));
        if (pointCode == null || pointCode.isEmpty()) {
            throw new GlobalException(ErrorCode.INVALID_POINT_CODE);
        }
    }

    public PointServeResponseDto findByPointName(String pointName) {
        Point point = pointRepository.findByPointName(pointName).orElseThrow(() -> new GlobalException(ErrorCode.INVALID_POINT_NAME));
        return PointServeResponseDto.fromPoint(point);
    }
}

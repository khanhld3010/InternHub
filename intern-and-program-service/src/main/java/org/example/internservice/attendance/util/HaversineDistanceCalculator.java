package org.example.internservice.attendance.util;

import org.springframework.stereotype.Component;

@Component
public class HaversineDistanceCalculator {

    private static final double EARTH_RADIUS_METERS = 6371000.0;

    /**
     * Tính khoảng cách giữa hai điểm tọa độ địa lý (WGS84) theo mét bằng công thức Haversine.
     *
     * @param lat1 Vĩ độ điểm 1
     * @param lon1 Kinh độ điểm 1
     * @param lat2 Vĩ độ điểm 2
     * @param lon2 Kinh độ điểm 2
     * @return Khoảng cách tính bằng mét (làm tròn 2 chữ số thập phân)
     */
    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = EARTH_RADIUS_METERS * c;

        return Math.round(distance * 100.0) / 100.0;
    }

    /**
     * Kiểm tra xem điểm tọa độ của người dùng có nằm trong bán kính cho phép của văn phòng hay không.
     */
    public boolean isWithinRadius(double userLat, double userLon, double officeLat, double officeLon, double allowedRadiusMeters) {
        return calculateDistance(userLat, userLon, officeLat, officeLon) <= allowedRadiusMeters;
    }
}

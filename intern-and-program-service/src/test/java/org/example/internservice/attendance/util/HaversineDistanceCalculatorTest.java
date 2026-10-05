package org.example.internservice.attendance.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HaversineDistanceCalculatorTest {

    private HaversineDistanceCalculator calculator;

    private final double officeLat = 21.028511;
    private final double officeLon = 105.854444;

    @BeforeEach
    void setUp() {
        calculator = new HaversineDistanceCalculator();
    }

    @Test
    @DisplayName("Tọa độ trùng nhau khoảng cách phải bằng 0 mét")
    void givenSameCoordinates_whenCalculate_thenDistanceIsZero() {
        double distance = calculator.calculateDistance(officeLat, officeLon, officeLat, officeLon);
        assertEquals(0.0, distance);
        assertTrue(calculator.isWithinRadius(officeLat, officeLon, officeLat, officeLon, 25.0));
    }

    @Test
    @DisplayName("Tọa độ dịch chuyển nhẹ trong khoảng 1m phải nằm trong bán kính 25m")
    void givenCoordinatesWithin25Meters_whenCheckRadius_thenReturnTrue() {
        // Thay đổi vĩ độ rất nhỏ: ~0.00001 độ tương đương khoảng 1.11 mét
        double userLat = 21.028520;
        double userLon = 105.854444;

        double distance = calculator.calculateDistance(userLat, userLon, officeLat, officeLon);
        assertTrue(distance < 2.0);
        assertTrue(calculator.isWithinRadius(userLat, userLon, officeLat, officeLon, 25.0));
    }

    @Test
    @DisplayName("Tọa độ cách xa hơn 100m phải vượt quá bán kính 25m")
    void givenCoordinatesBeyond25Meters_whenCheckRadius_thenReturnFalse() {
        // Thay đổi vĩ độ ~0.001 độ tương đương hơn 100 mét
        double userLat = 21.030000;
        double userLon = 105.854444;

        double distance = calculator.calculateDistance(userLat, userLon, officeLat, officeLon);
        assertTrue(distance > 100.0);
        assertFalse(calculator.isWithinRadius(userLat, userLon, officeLat, officeLon, 25.0));
    }
}

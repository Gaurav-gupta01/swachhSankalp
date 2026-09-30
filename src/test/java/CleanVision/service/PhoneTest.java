package CleanVision.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PhoneTest {

    @Test
    void validIndianMobileFormatsAreNormalised() {
        assertThat(Phone.normalize("9876543210")).isEqualTo("9876543210");
        assertThat(Phone.normalize("+91 98765 43210")).isEqualTo("9876543210");
        assertThat(Phone.normalize("91-98765-43210")).isEqualTo("9876543210");
        assertThat(Phone.normalize("09876543210")).isEqualTo("9876543210");
    }

    @Test
    void invalidMobilesAreRejected() {
        assertThatThrownBy(() -> Phone.normalize("12345")).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> Phone.normalize("5876543210")).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> Phone.normalize(null)).isInstanceOf(AppException.class);
    }

    @Test
    void isValidMobileAnswersWithoutThrowing() {
        assertThat(Phone.isValidMobile("9876543210")).isTrue();
        assertThat(Phone.isValidMobile("5876543210")).isFalse();
        assertThat(Phone.isValidMobile(null)).isFalse();
        assertThat(Phone.isValidMobile("")).isFalse();
    }
}

ifeq ($(strip $(DEVKITPRO)),)
$(error Set DEVKITPRO to your devkitPro installation)
endif
include $(DEVKITPRO)/libnx/switch_rules

.DEFAULT_GOAL := all
TARGET := SunnyTV
PKG := aarch64-none-elf-pkg-config
PACKAGES := mpv sdl2 SDL2_ttf SDL2_image libcurl
ARCH := -march=armv8-a+crc+crypto -mtune=cortex-a57 -mtp=soft -fPIE -ftls-model=local-exec
CXXFLAGS := $(ARCH) -std=gnu++20 -O2 -g -Wall -Wextra -Wno-deprecated-declarations -ffunction-sections -fdata-sections -D__SWITCH__ -Iinclude -I$(DEVKITPRO)/libnx/include $(shell $(PKG) --cflags $(PACKAGES))
LDFLAGS := $(ARCH) -specs=$(DEVKITPRO)/libnx/switch.specs -Wl,--gc-sections -Wl,-Map,build/SunnyTV.map
LIBS := -Wl,--start-group $(shell $(PKG) --static --libs $(PACKAGES)) -L$(DEVKITPRO)/libnx/lib -lnx -lm -Wl,--end-group
SOURCES := $(wildcard source/*.cpp)
OBJECTS := $(patsubst source/%.cpp,build/%.o,$(SOURCES))

.PHONY: all clean check-deps package
all: check-deps $(TARGET).nro
check-deps:
	@$(PKG) --exists $(PACKAGES)
build:
	@mkdir -p build
build/%.o: source/%.cpp | build
	$(CXX) $(CXXFLAGS) -MMD -MP -c $< -o $@
$(TARGET).elf: $(OBJECTS)
	$(CXX) $(LDFLAGS) $(OBJECTS) $(LIBS) -o $@
$(TARGET).nacp:
	nacptool --create "SunnyTV" "SunnyTV contributors" "0.2.8" $@
$(TARGET).nro: $(TARGET).elf $(TARGET).nacp
	elf2nro $< $@ --nacp=$(TARGET).nacp --icon=$(DEVKITPRO)/libnx/default_icon.jpg
package: all
	mkdir -p dist/switch/SunnyTV
	cp SunnyTV.nro dist/switch/SunnyTV/
	cp README.md dist/README.txt
	cp ../LICENSE dist/LICENSE.txt
	cp THIRD_PARTY.md dist/THIRD_PARTY.txt
	sha256sum SunnyTV.nro > dist/SHA256SUMS.txt
clean:
	rm -rf build dist $(TARGET).elf $(TARGET).nacp $(TARGET).nro
-include $(OBJECTS:.o=.d)

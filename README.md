# QuantumAdders

Paper 서버용 커스텀 아이템 · 블록 플러그인. YAML 정의 하나로 **자바와 베드락(Geyser) 양쪽에** 아이템과 블록을 추가하고, 두 플랫폼의 리소스팩을 자동으로 만들어 줍니다.

> 개발 중입니다. 아이템과 기본형 블록이 동작하며, 엔티티는 아직 없습니다.

## 요구 사항

| | |
|---|---|
| 서버 | Paper **26.2** 이상 |
| 자바 | **25** |
| 베드락 (선택) | Geyser **2.11** 이상 — 없으면 자바 전용으로 동작 |

## 빌드

```bash
mvn clean package
```

`target/QuantumAdders-0.1.0.jar`을 `plugins/`에 넣고 서버를 재시작하면 됩니다.

## 폴더 구조

첫 실행 시 `plugins/QuantumAdders/`에 만들어집니다.

```
items/          아이템 정의 (*.yml)
blocks/         블록 정의 (*.yml)
textures/       PNG 원본 — 여기에 넣으면 양쪽 팩에 들어갑니다
lang/           ko_kr.yml, en_us.yml
output/         생성된 리소스팩
cache/          팩 작업 폴더 (건드릴 필요 없음)
config.yml
block-states.yml   블록↔note block 상태 배정 기록 (자동 생성)
```

## 네임스페이스

모든 아이템과 블록이 이 이름 아래 놓입니다. 자바 모델 키는 `<namespace>:<id>`, 베드락 텍스처 키는 `<namespace>_<id>`가 됩니다.

```yaml
namespace: quantumadders
```

**한 번 아이템을 만든 뒤에는 바꾸지 마세요.** 이미 지급된 아이템의 모델 키가 어긋나 텍스처를 잃습니다.

## 아이템

`items/` 안의 모든 `.yml`을 읽습니다.

```yaml
items:
  ruby:
    base: PAPER                    # 바탕이 될 바닐라 아이템
    display-name: "<red>루비"       # MiniMessage
    lore:
      - "<dark_gray>순수한 결정"
    texture: ruby                  # textures/ruby.png
    max-stack-size: 64
    model-parent: minecraft:item/handheld   # 생략하면 도구/무기는 자동으로 handheld
    bedrock:
      creative-category: ITEMS     # none, construction, nature, equipment, items
      creative-group: ""
      display-handheld: false
      allow-offhand: true
      protection-value: 0
```

아이템은 바닐라 아이템에 `item_model` 컴포넌트를 씌운 것이라 스택·소각·획득이 전부 바닐라대로 동작합니다.

## 블록

`blocks/` 안의 모든 `.yml`을 읽습니다. 블록마다 **같은 id의 아이템이 자동 생성**되어 손에 들고 설치할 수 있습니다.

### 텍스처 — 세 가지 방식

좁은 지정이 넓은 지정을 이깁니다: **개별 면 > `side` > `all`**

```yaml
blocks:
  # 전부 같게
  ruby_block:
    texture: ruby_block

  # 위 / 아래 / 옆 (기둥)
  ruby_pillar:
    textures:
      top: ruby_pillar_top
      bottom: ruby_pillar_top
      side: ruby_pillar_side       # 네 벽면 한 번에

  # 면마다 다르게
  ruby_machine:
    textures:
      all: machine_side            # 기본값
      up: machine_top
      down: machine_bottom
      north: machine_front         # 앞면만 따로
```

여러 블록이 같은 PNG를 써도 팩에는 한 번만 들어갑니다.

### 드롭 테이블 · 경험치

```yaml
  ruby_ore:
    hardness: 4.5                  # 채굴 시간. 돌 1.5, 흑요석 50
    light-emission: 0              # 0~15
    xp: "2-5"                      # 고정값 또는 범위
    silk-touch-self: true          # 실크터치면 블록 자체, 경험치 없음

    drops:                         # 없으면 그냥 자기 자신 1개
      - item: ruby                 # 커스텀 아이템 id 또는 바닐라 이름
        amount: "1-3"
        chance: 1.0
        fortune: true              # 행운 레벨만큼 배수
      - item: COBBLESTONE
        amount: 1
        chance: 0.25
```

크리에이티브에서는 아무것도 떨구지 않습니다.

## 명령어

`/quantumadders` (별칭 `/qa`) · 권한 `quantumadders.admin` (기본 OP)

| 명령어 | 설명 |
|---|---|
| `/qa give <대상> <id> [개수]` | 아이템·블록 지급 |
| `/qa list` | 아이템·블록 목록과 상태 사용량 |
| `/qa reload` | 설정·정의·언어 다시 읽기 |
| `/qa pack` | 리소스팩 다시 생성 |

## 리소스팩

정의를 바꾸고 `/qa pack`을 실행하면 두 팩이 새로 만들어집니다. `auto-build-packs`가 켜져 있으면 서버 시작과 `/qa reload` 때도 자동으로 만들어집니다.

```yaml
auto-build-packs: true

pack:
  output: "output"                 # 상대 경로는 플러그인 폴더 기준, 절대 경로도 가능
  java:
    format: 88                     # 26.2 기준
    output: ""                     # 비우면 pack.output 사용
  bedrock:
    auto-install: true             # Geyser packs 폴더로 자동 복사
    output: ""
```

지정한 위치에는 **완성된 파일만** 쓰이고 그 폴더의 다른 내용은 건드리지 않습니다. 작업 파일은 항상 `cache/`에 있습니다.

베드락 팩은 Geyser 폴더로 자동 설치되지만, **자바 팩은 직접 호스팅하거나 서버 리소스팩으로 지정해야** 합니다. ResourcePackManager를 쓴다면 자바 팩 출력을 그쪽 `mixer/`로 보내면 됩니다.

```yaml
  java:
    output: "../ResourcePackManager/mixer/QuantumAdders-java.zip"
```

## Geyser 연동

Geyser가 **어디서 돌아가느냐**에 따라 경로가 갈립니다.

- **같은 서버의 플러그인일 때** — `GeyserDefineCustomItemsEvent`로 직접 등록합니다. `plugin.yml`의 `loadbefore`가 Geyser보다 먼저 로드되도록 보장합니다.
- **외부(standalone·프록시)일 때** — API로 닿을 수 없으므로 매핑 파일을 생성합니다. `output/custom_mappings/`의 두 JSON을 외부 Geyser의 `custom_mappings/` 폴더에, `.mcpack`을 `packs/` 폴더에 복사하고 Geyser를 재시작하세요.

```yaml
geyser:
  write-mappings: true
  mappings-output: ""              # 외부 Geyser 폴더를 직접 지정하면 복사 단계가 없어집니다
```

아이템과 블록 매핑이 별도 파일인 것은 Geyser가 타입별로 다른 리더를 쓰기 때문입니다.

## 언어

메시지는 전부 `lang/`에 있습니다. 기본은 `ko_kr`이고, 파일을 복사해 이름을 바꾸면 언어를 추가할 수 있습니다.

```yaml
language: ko_kr
```

값은 MiniMessage이고 자리표시자는 `<item>`, `<count>` 같은 태그입니다. 태그라서 번역할 때 **순서를 바꾸거나 빼도** 됩니다. 버전이 올라 키가 늘어나도 jar에 들어 있는 같은 이름 파일이 빈자리를 채웁니다.

## 알아둘 점

**블록은 note block 상태를 빌려 씁니다.** 바닐라 서버는 진짜 새 블록을 만들 수 없어서, ItemsAdder·Oraxen과 같은 방식입니다. 여기서 몇 가지가 따라옵니다.

- **최대 약 674개** (악기 27 × 음 25 − 일반 note block 몫 1). 정확한 값은 `/qa list`에 나옵니다.
- **`block-states.yml`을 지우거나 고치지 마세요.** 블록의 정체는 좌표가 아니라 상태에 담겨 있어서, 배정이 바뀌면 월드에 이미 지어놓은 블록이 전부 다른 블록으로 보입니다. 블록을 삭제해도 그 상태는 회수하지 않습니다.
- 커스텀 블록은 우클릭해도 음이 바뀌지 않고 레드스톤에도 반응하지 않습니다. **일반 note block은 그대로 연주됩니다.**
- 자바 팩이 `assets/minecraft/blockstates/note_block.json`을 덮어쓰므로, note block을 재정의하는 다른 팩과 충돌합니다. 팩을 병합해 쓴다면 우선순위를 확인하세요.

`/qa reload`는 자바 쪽만 갱신합니다. **Geyser는 시작할 때 만든 목록을 그대로 쓰므로, 베드락에 반영하려면 서버를 재시작해야 합니다.**

## 라이선스

MIT — [LICENSE](LICENSE) 참고.

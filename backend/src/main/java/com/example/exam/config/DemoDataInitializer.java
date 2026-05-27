package com.example.exam.config;

import com.example.exam.dto.AiSettingsDtos.AiSettingsResponse;
import com.example.exam.model.FileTag;
import com.example.exam.model.KnowledgeChunk;
import com.example.exam.model.KnowledgeChunkEvent;
import com.example.exam.model.KnowledgeChunkEventType;
import com.example.exam.model.MistakePracticeEvent;
import com.example.exam.model.MistakeQuestion;
import com.example.exam.model.MistakeQuestionChunk;
import com.example.exam.model.MistakeQuestionChunkSourceType;
import com.example.exam.model.MistakeStatus;
import com.example.exam.model.MistakeSubjectTag;
import com.example.exam.model.StudyFile;
import com.example.exam.model.StudyFolder;
import com.example.exam.model.StudyPlanItem;
import com.example.exam.model.StudyPlanItemType;
import com.example.exam.model.StudyPlanPriority;
import com.example.exam.model.StudyPlanSource;
import com.example.exam.model.StudyPlanStatus;
import com.example.exam.model.User;
import com.example.exam.model.UserAiSettings;
import com.example.exam.model.UserStudyProfile;
import com.example.exam.repository.KnowledgeChunkEventRepository;
import com.example.exam.repository.KnowledgeChunkRepository;
import com.example.exam.repository.MistakePracticeEventRepository;
import com.example.exam.repository.MistakeQuestionChunkRepository;
import com.example.exam.repository.MistakeQuestionRepository;
import com.example.exam.repository.MistakeStatusRepository;
import com.example.exam.repository.MistakeSubjectTagRepository;
import com.example.exam.repository.StudyFileRepository;
import com.example.exam.repository.StudyFolderRepository;
import com.example.exam.repository.StudyPlanItemRepository;
import com.example.exam.repository.UserAiSettingsRepository;
import com.example.exam.repository.UserRepository;
import com.example.exam.repository.UserStudyProfileRepository;
import com.example.exam.service.AiSettingsService;
import com.example.exam.service.ElasticsearchService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "app.demo-data", name = "enabled", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {
    public static final String DEMO_USERNAME = "demo_student";
    public static final String DEMO_PASSWORD = "Demo@123456";

    private static final int CHUNKING_VERSION = 2;
    private static final LocalDate ACTIVITY_START_DATE = LocalDate.of(2026, 4, 20);

    private final UserRepository userRepository;
    private final UserStudyProfileRepository profileRepository;
    private final UserAiSettingsRepository aiSettingsRepository;
    private final StudyFolderRepository folderRepository;
    private final StudyFileRepository fileRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeChunkEventRepository chunkEventRepository;
    private final MistakeStatusRepository statusRepository;
    private final MistakeSubjectTagRepository subjectTagRepository;
    private final MistakeQuestionRepository mistakeRepository;
    private final MistakeQuestionChunkRepository mistakeChunkRepository;
    private final MistakePracticeEventRepository practiceEventRepository;
    private final StudyPlanItemRepository planItemRepository;
    private final PasswordEncoder passwordEncoder;
    private final ElasticsearchService elasticsearchService;
    private final Path uploadDir;

    public DemoDataInitializer(UserRepository userRepository,
                               UserStudyProfileRepository profileRepository,
                               UserAiSettingsRepository aiSettingsRepository,
                               StudyFolderRepository folderRepository,
                               StudyFileRepository fileRepository,
                               KnowledgeChunkRepository chunkRepository,
                               KnowledgeChunkEventRepository chunkEventRepository,
                               MistakeStatusRepository statusRepository,
                               MistakeSubjectTagRepository subjectTagRepository,
                               MistakeQuestionRepository mistakeRepository,
                               MistakeQuestionChunkRepository mistakeChunkRepository,
                               MistakePracticeEventRepository practiceEventRepository,
                               StudyPlanItemRepository planItemRepository,
                               PasswordEncoder passwordEncoder,
                               ElasticsearchService elasticsearchService,
                               @Value("${app.upload-dir}") String uploadDir) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.aiSettingsRepository = aiSettingsRepository;
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
        this.chunkRepository = chunkRepository;
        this.chunkEventRepository = chunkEventRepository;
        this.statusRepository = statusRepository;
        this.subjectTagRepository = subjectTagRepository;
        this.mistakeRepository = mistakeRepository;
        this.mistakeChunkRepository = mistakeChunkRepository;
        this.practiceEventRepository = practiceEventRepository;
        this.planItemRepository = planItemRepository;
        this.passwordEncoder = passwordEncoder;
        this.elasticsearchService = elasticsearchService;
        this.uploadDir = Path.of(uploadDir);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        User user = userRepository.findByUsername(DEMO_USERNAME).orElseGet(this::newDemoUser);
        resetDemoUser(user);
        clearDemoBusinessData(user);

        createStudyProfile(user);
        createAiSettings(user);

        Map<String, StudyFolder> folders = createFolders(user);
        Map<String, MistakeSubjectTag> tags = createSubjectTags(user);
        Map<String, MistakeStatus> statuses = createMistakeStatuses(user);
        List<KnowledgeChunk> chunks = createKnowledgeBase(user, folders);
        createKnowledgeEvents(user, chunks);
        List<MistakeQuestion> mistakes = createMistakes(user, tags, statuses, chunks);
        createMistakeEvents(user, mistakes);
        createStudyPlan(user);
        reindexDemoFiles(user);
    }

    private User newDemoUser() {
        User user = new User();
        user.setUsername(DEMO_USERNAME);
        return user;
    }

    private void resetDemoUser(User user) {
        user.setDisplayName("林知远");
        user.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        userRepository.save(user);
    }

    private void clearDemoBusinessData(User user) {
        Long userId = user.getId();
        List<StudyFolder> folders = folderRepository.findByOwnerIdOrderByCreatedAtDesc(userId);
        Set<Long> folderIds = folders.stream().map(StudyFolder::getId).collect(java.util.stream.Collectors.toSet());
        List<StudyFile> files = folderIds.isEmpty() ? List.of() : fileRepository.findByFolderIdIn(folderIds);
        List<KnowledgeChunk> chunks = files.stream()
                .flatMap(file -> chunkRepository.findByFileIdOrderByChunkIndexAsc(file.getId()).stream())
                .toList();
        List<MistakeQuestion> mistakes = mistakeRepository.findByOwnerIdOrderByUpdatedAtDesc(userId);
        List<Long> mistakeIds = mistakes.stream().map(MistakeQuestion::getId).toList();

        if (!mistakeIds.isEmpty()) {
            mistakeChunkRepository.deleteAll(mistakeChunkRepository.findByMistakeIdIn(mistakeIds));
        }
        mistakes.forEach(mistake -> mistake.getSubjectTags().clear());
        mistakeRepository.deleteAll(mistakes);
        practiceEventRepository.deleteAll(practiceEventRepository.findAll().stream()
                .filter(event -> userId.equals(event.getOwnerId()))
                .toList());
        chunkEventRepository.deleteAll(chunkEventRepository.findAll().stream()
                .filter(event -> userId.equals(event.getOwnerId()))
                .toList());
        chunkRepository.deleteAll(chunks);
        fileRepository.deleteAll(files);
        folderRepository.deleteAll(folders.stream()
                .sorted(Comparator.comparingInt(StudyFolder::getDepth).reversed())
                .toList());
        planItemRepository.deleteAll(planItemRepository.findAll().stream()
                .filter(item -> item.getOwner().getId().equals(userId))
                .toList());
        statusRepository.deleteAll(statusRepository.findByOwnerIdOrderByCreatedAtAsc(userId));
        subjectTagRepository.deleteAll(subjectTagRepository.findByOwnerIdOrderByCreatedAtAsc(userId));

        mistakeChunkRepository.flush();
        mistakeRepository.flush();
        practiceEventRepository.flush();
        chunkEventRepository.flush();
        chunkRepository.flush();
        fileRepository.flush();
        folderRepository.flush();
        planItemRepository.flush();
        statusRepository.flush();
        subjectTagRepository.flush();
    }

    private void createStudyProfile(User user) {
        UserStudyProfile profile = profileRepository.findByOwnerId(user.getId()).orElseGet(UserStudyProfile::new);
        profile.setOwner(user);
        profile.setExamDate(LocalDate.of(2026, 12, 20));
        profile.setOnboarded(true);
        profile.setSubjectCount(4);
        profileRepository.save(profile);
    }

    private void createAiSettings(User user) {
        UserAiSettings settings = aiSettingsRepository.findByUserId(user.getId()).orElseGet(UserAiSettings::new);
        settings.setUser(user);
        settings.setAiRole("严谨的考研答疑老师");
        settings.setSystemPrompt(AiSettingsService.DEFAULT_SYSTEM_PROMPT);
        settings.setChatModel(AiSettingsService.DEFAULT_CHAT_MODEL);
        settings.setChatEndpoint(AiSettingsService.DEFAULT_CHAT_ENDPOINT);
        settings.setChatApiKey("");
        settings.setEmbeddingModel("text-embedding-v4");
        settings.setEmbeddingEndpoint(AiSettingsService.DEFAULT_EMBEDDING_ENDPOINT);
        settings.setEmbeddingApiKey("");
        settings.setEmbeddingDimensions(1536);
        settings.setPresetsJson("""
                [{"id":"demo-embedding-v4","name":"演示：text-embedding-v4","updatedAt":1779235200000,"settings":{"aiRole":"严谨的考研答疑老师","systemPrompt":%s,"chatModel":"gpt-4o-mini","chatEndpoint":"https://api.openai.com/v1/chat/completions","chatApiKey":"","embeddingModel":"text-embedding-v4","embeddingEndpoint":"https://api.openai.com/v1/embeddings","embeddingApiKey":"","embeddingDimensions":1536}}]
                """.formatted(jsonString(AiSettingsService.DEFAULT_SYSTEM_PROMPT)).trim());
        aiSettingsRepository.save(settings);
    }

    private String jsonString(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n")
                + "\"";
    }

    private Map<String, StudyFolder> createFolders(User user) {
        Map<String, StudyFolder> folders = new LinkedHashMap<>();
        StudyFolder math = rootFolder(user, "考研数学", "高数、线代与概率论复习资料", 0);
        StudyFolder english = rootFolder(user, "考研英语", "阅读、长难句、翻译、作文", 1);
        StudyFolder politics = rootFolder(user, "考研政治", "马原、史纲、思修、毛中特与时政", 2);
        StudyFolder cs = rootFolder(user, "408计算机基础", "数据结构、计组、操作系统、计算机网络", 3);
        folders.put(math.getName(), math);
        folders.put(english.getName(), english);
        folders.put(politics.getName(), politics);
        folders.put(cs.getName(), cs);
        folders.put("高等数学", childFolder(user, math, "高等数学", "极限、导数、积分与级数"));
        folders.put("线性代数", childFolder(user, math, "线性代数", "矩阵、向量组、特征值"));
        folders.put("概率论", childFolder(user, math, "概率论", "随机变量、分布、数字特征"));
        folders.put("阅读理解", childFolder(user, english, "阅读理解", "真题阅读方法与错因整理"));
        folders.put("长难句与翻译", childFolder(user, english, "长难句与翻译", "从句、非谓语、翻译拆分"));
        folders.put("作文素材", childFolder(user, english, "作文素材", "大小作文结构与表达"));
        folders.put("马原", childFolder(user, politics, "马原", "哲学、政治经济学核心概念"));
        folders.put("史纲与思修", childFolder(user, politics, "史纲与思修", "时间线、人物、价值观考点"));
        folders.put("数据结构", childFolder(user, cs, "数据结构", "树、图、排序、查找"));
        folders.put("操作系统", childFolder(user, cs, "操作系统", "进程、内存、文件与 I/O"));
        folders.put("计算机网络", childFolder(user, cs, "计算机网络", "协议分层、TCP/IP、路由"));
        return folders;
    }

    private StudyFolder rootFolder(User user, String name, String description, int order) {
        StudyFolder folder = new StudyFolder();
        folder.setOwner(user);
        folder.setName(name);
        folder.setDescription(description);
        folder.setDepth(1);
        folder.setSubjectFolder(true);
        folder.setSubjectOrder(order);
        return folderRepository.save(folder);
    }

    private StudyFolder childFolder(User user, StudyFolder parent, String name, String description) {
        StudyFolder folder = new StudyFolder();
        folder.setOwner(user);
        folder.setParent(parent);
        folder.setName(name);
        folder.setDescription(description);
        folder.setDepth(parent.getDepth() + 1);
        return folderRepository.save(folder);
    }

    private Map<String, MistakeSubjectTag> createSubjectTags(User user) {
        Map<String, MistakeSubjectTag> tags = new LinkedHashMap<>();
        for (String name : List.of("考研数学", "考研英语", "考研政治", "408计算机基础")) {
            MistakeSubjectTag tag = new MistakeSubjectTag();
            tag.setOwner(user);
            tag.setName(name);
            tags.put(name, subjectTagRepository.save(tag));
        }
        return tags;
    }

    private Map<String, MistakeStatus> createMistakeStatuses(User user) {
        Map<String, MistakeStatus> statuses = new LinkedHashMap<>();
        for (String name : List.of("未掌握", "需要二刷", "已订正", "考前再看", "公式不熟", "概念混淆", "二刷重点", "审题失误", "方法迁移")) {
            MistakeStatus status = new MistakeStatus();
            status.setOwner(user);
            status.setName(name);
            statuses.put(name, statusRepository.save(status));
        }
        return statuses;
    }

    private List<KnowledgeChunk> createKnowledgeBase(User user, Map<String, StudyFolder> folders) throws IOException {
        List<DemoFile> files = List.of(
                new DemoFile("math", folders.get("高等数学"), FileTag.NOTE, "高数极限与连续复习提纲.md", """
                        # 极限与连续

                        数列极限的核心是从任意小的误差出发，找到足够靠后的项，使之后所有项都落在误差范围内。常用方法包括夹逼准则、单调有界准则和等价无穷小替换。

                        函数连续要求函数值、左极限和右极限三者一致。间断点可以分为可去间断、跳跃间断和无穷间断。做题时先判断定义点是否存在，再比较左右极限。

                        洛必达法则适用于未定式，但使用前必须检查分子分母同时趋于零或无穷，并且导数极限存在。遇到乘积、幂指型问题，常先变形为商式或取对数。
                        """),
                new DemoFile("math", folders.get("高等数学"), FileTag.MATERIAL, "高数导数与中值定理.md", """
                        # 导数与中值定理

                        导数表示函数在一点处的瞬时变化率。可导一定连续，但连续不一定可导，尖点、垂直切线和震荡都可能破坏可导性。

                        罗尔定理要求闭区间连续、开区间可导，并且端点函数值相等。拉格朗日中值定理可以看作罗尔定理的推广，用来建立函数增减性与导数符号的关系。

                        泰勒公式常用于局部近似和极限计算。使用时要根据题目精度保留到合适阶数，余项阶数过低会导致结论错误。
                        """),
                new DemoFile("math", folders.get("高等数学"), FileTag.EXERCISE, "高数积分方法与错题订正.md", """
                        # 积分方法

                        不定积分优先识别基本公式、凑微分和换元结构。遇到乘积形式时，可以考虑分部积分，选择 u 时通常让求导后变简单的部分作为 u。

                        定积分除了计算，还要关注对称性、周期性和区间变换。奇函数在对称区间积分为零，偶函数可转化为二倍半区间积分。

                        反常积分要先判断收敛性，再进行计算。无穷区间和瑕点积分都需要写成极限形式，不能直接套普通定积分结论。
                        """),
                new DemoFile("math", folders.get("线性代数"), FileTag.MATERIAL, "线代矩阵秩与特征值速记.md", """
                        # 矩阵秩与特征值

                        矩阵的秩等于其行向量组或列向量组的最大线性无关组所含向量个数。初等行变换不改变矩阵秩，因此阶梯形矩阵中非零行数就是秩。

                        齐次线性方程组 Ax=0 有非零解的充要条件是矩阵 A 的秩小于未知量个数。非齐次方程组有解的条件是系数矩阵和增广矩阵秩相等。

                        特征值和特征向量由方程 Ax=lambda x 定义。不同特征值对应的特征向量线性无关。实对称矩阵一定可以正交相似对角化。
                        """),
                new DemoFile("math", folders.get("概率论"), FileTag.NOTE, "概率论随机变量与数字特征.md", """
                        # 随机变量与数字特征

                        离散型随机变量用分布律描述，连续型随机变量用密度函数描述。分布函数具有单调不减、右连续和取值范围在 0 到 1 之间的性质。

                        数学期望刻画平均水平，方差刻画波动程度。计算方差时常用公式 D(X)=E(X^2)-[E(X)]^2，可以减少展开量。

                        常见分布要熟悉适用场景。二项分布对应重复独立试验，泊松分布常用于稀有事件计数，正态分布常用于近似和标准化。
                        """),
                new DemoFile("english", folders.get("阅读理解"), FileTag.NOTE, "英语阅读定位与干扰项.md", """
                        # 阅读理解方法

                        阅读题先识别题干关键词，再回到原文定位同义替换。定位句本身通常不足以直接作答，需要结合前后一句判断作者态度和逻辑关系。

                        干扰项常见方式包括偷换范围、因果倒置、过度推断和原文词汇拼贴。正确选项往往不是原句复写，而是对原文信息的概括转述。

                        态度题要关注形容词、副词、转折词和引号语境。遇到 however、yet、nevertheless 之后的信息，优先判断为作者真正强调的方向。
                        """),
                new DemoFile("english", folders.get("阅读理解"), FileTag.EXERCISE, "英语阅读真题错因记录.md", """
                        # 阅读错因记录

                        主旨题要避免被局部细节带偏。段落首尾句、重复出现的关键词和转折后的结论，通常比某个例子本身更能代表主旨。

                        推断题的答案必须建立在原文线索上，不允许加入常识脑补。选项如果看起来合理但原文没有支撑，应优先排除。

                        例证题先定位例子服务的观点。例子本身不是答案，例子前后的概括性句子才是命题人想考的逻辑。
                        """),
                new DemoFile("english", folders.get("长难句与翻译"), FileTag.MATERIAL, "长难句拆分与翻译步骤.md", """
                        # 长难句拆分

                        长难句先找谓语动词，再确定主干。定语从句、状语从句、同位语和插入语可以先用括号标出，避免主干被修饰成分遮住。

                        翻译时不必逐词对应，应先保证中文语序自然。被动结构可以视语境译为主动，名词化结构可以转换为动词表达。

                        遇到多重从句时，先翻译最内层信息，再按逻辑关系还原到主句。转折、因果和让步关系要在译文中明确体现。
                        """),
                new DemoFile("english", folders.get("作文素材"), FileTag.NOTE, "英语作文图表表达模板.md", """
                        # 作文表达

                        图表作文第一段应准确描述趋势，避免机械罗列数字。可以使用 increase steadily、decline slightly、remain stable 等表达描述变化。

                        第二段解释原因时，建议从社会环境、个人选择和技术发展三个层面组织。每个原因后补一个简短例子，可以提升论证具体性。

                        结尾段要回扣主题并给出建议。建议使用 concise but practical 的表达，避免空泛口号。
                        """),
                new DemoFile("politics", folders.get("马原"), FileTag.TEXTBOOK, "马原矛盾分析法.md", """
                        # 矛盾分析法

                        矛盾具有普遍性和特殊性。普遍性说明矛盾存在于一切事物发展过程中，特殊性要求具体问题具体分析，不能用一个模板解释所有问题。

                        主要矛盾是在复杂事物发展过程中居于支配地位、对事物发展起决定作用的矛盾。矛盾的主要方面决定事物性质。

                        做材料分析题时，应先指出原理，再结合材料关键词说明矛盾双方的关系，最后落到方法论要求。
                        """),
                new DemoFile("politics", folders.get("马原"), FileTag.NOTE, "马原实践认识论.md", """
                        # 实践与认识

                        实践是认识的来源、动力、目的和检验标准。认识从实践中来，又回到实践中去指导新的实践。

                        感性认识包括感觉、知觉和表象，理性认识包括概念、判断和推理。二者相互依赖，感性认识有待上升为理性认识。

                        真理具有客观性、绝对性和相对性。真理和谬误在一定条件下可以相互转化，因此要在实践中不断检验和发展认识。
                        """),
                new DemoFile("politics", folders.get("史纲与思修"), FileTag.MATERIAL, "史纲时间线与会议整理.md", """
                        # 史纲时间线

                        近代史复习要抓住时间线、主要矛盾变化和历史任务。不同阶段的关键词能够帮助快速定位材料背景。

                        重要会议题常考会议时间、地点、主要内容和历史意义。复习时建议把会议放回时代背景，而不是孤立背诵。

                        评价历史事件时，要从性质、意义、局限和经验启示四个角度组织答案，避免只写结论。
                        """),
                new DemoFile("politics", folders.get("史纲与思修"), FileTag.NOTE, "思修法基价值观与法治.md", """
                        # 思修法基

                        人生价值包括自我价值和社会价值。评价人生价值的根本尺度，是个体实践活动是否符合社会发展和人民需要。

                        社会主义核心价值观分为国家、社会和个人三个层面。答题时要区分富强、民主、文明、和谐，自由、平等、公正、法治，爱国、敬业、诚信、友善。

                        法治思维强调规则意识、权利义务统一和程序正当。遇到案例题时，要先判断法律关系，再分析权利和责任。
                        """),
                new DemoFile("cs", folders.get("数据结构"), FileTag.MATERIAL, "数据结构树与图高频考点.md", """
                        # 树与图

                        二叉树的遍历包括先序、中序、后序和层序。已知中序序列和先序或后序序列，可以唯一确定一棵二叉树。

                        哈夫曼树用于构造带权路径长度最小的二叉树。权值越大的结点离根越近，编码时不存在任何一个编码是另一个编码前缀的情况。

                        图的遍历包括深度优先搜索和广度优先搜索。拓扑排序只适用于有向无环图，如果排序过程中无法找到入度为零的顶点，则说明存在环。
                        """),
                new DemoFile("cs", folders.get("数据结构"), FileTag.EXERCISE, "数据结构排序与查找错题.md", """
                        # 排序与查找

                        快速排序平均时间复杂度为 O(n log n)，最坏情况下为 O(n^2)。当划分极不均衡时，递归深度会明显增加。

                        堆排序不稳定，但额外空间复杂度较低。归并排序稳定，适合外部排序，但需要额外辅助空间。

                        二分查找要求顺序表有序。散列表查找效率受装填因子和冲突处理方式影响，常见冲突处理包括开放定址和链地址法。
                        """),
                new DemoFile("cs", folders.get("操作系统"), FileTag.NOTE, "操作系统进程同步与死锁.md", """
                        # 进程同步与死锁

                        进程是资源分配的基本单位，线程是处理机调度的基本单位。临界区问题要求同一时刻最多只有一个进程进入访问共享资源的代码段。

                        信号量 P 操作表示申请资源或进入临界区，V 操作表示释放资源或唤醒等待进程。互斥信号量通常初始化为 1，资源信号量初始化为资源数量。

                        死锁产生需要互斥、不可剥夺、请求并保持、循环等待四个必要条件。预防死锁可以破坏其中任意一个条件，银行家算法属于避免死锁。
                        """),
                new DemoFile("cs", folders.get("操作系统"), FileTag.EXERCISE, "操作系统调度例题订正.md", """
                        # 调度例题订正

                        先来先服务算法实现简单，但短作业可能长时间等待。短作业优先平均等待时间较短，但可能导致长作业饥饿。

                        时间片轮转适合分时系统，时间片过大会退化为先来先服务，时间片过小会增加上下文切换开销。

                        优先级调度需要区分抢占式和非抢占式。为了缓解低优先级进程长期等待，可以采用动态优先级或老化机制。
                        """),
                new DemoFile("cs", folders.get("计算机网络"), FileTag.MATERIAL, "计算机网络 TCP 与拥塞控制.md", """
                        # TCP 与拥塞控制

                        TCP 提供可靠传输，核心机制包括序号、确认、超时重传、滑动窗口和流量控制。流量控制关注接收方缓存能力，拥塞控制关注网络整体承载能力。

                        三次握手用于建立连接，四次挥手用于释放连接。TIME_WAIT 状态可以保证最后一个确认报文可能丢失时仍能重传。

                        拥塞控制包括慢开始、拥塞避免、快重传和快恢复。拥塞窗口会根据网络拥塞程度动态变化。
                        """),
                new DemoFile("math", folders.get("高等数学"), FileTag.MATERIAL, "高数强化专题大纲与易错清单.md", largeMathReview()),
                new DemoFile("english", folders.get("阅读理解"), FileTag.MATERIAL, "英语阅读强化课笔记与题型归纳.md", largeEnglishReview()),
                new DemoFile("politics", folders.get("马原"), FileTag.MATERIAL, "政治马原原理体系与材料题模板.md", largePoliticsReview()),
                new DemoFile("cs", folders.get("操作系统"), FileTag.MATERIAL, "408操作系统与网络综合复盘.md", largeCsReview())
        );

        List<KnowledgeChunk> chunks = new ArrayList<>();
        Map<String, Integer> subjectCounters = new LinkedHashMap<>();
        for (DemoFile demoFile : files) {
            StudyFile file = saveDemoFile(user, demoFile);
            chunks.addAll(saveChunks(file, demoFile.subjectKey(), subjectCounters));
        }
        return chunks;
    }

    private String largeMathReview() {
        return """
                # 高数强化专题大纲与易错清单

                ## 一、极限计算总框架

                极限题先判断表达式类型，再选择工具。代入能直接得到确定值时不要过度变形；出现 0/0、无穷/无穷时，优先考虑等价无穷小、泰勒展开、洛必达法则和夹逼。使用洛必达前要确认未定式成立，使用泰勒展开时要保留到能区分首个非零项的阶数。

                常见错误是把等价无穷小用于加减结构，例如把 sin x - x 直接替换为 0 会丢失主导项。遇到加减结构应先通分、提取或展开到更高阶，再判断主导项。含参数极限通常需要根据极限存在、连续或可导条件反推参数。

                ## 二、连续与间断点

                连续性判断包含函数值、左极限和右极限三个对象。分段函数在分界点处最容易出错，要分别计算左右极限，并与函数值比较。可去间断点通常是极限存在但函数值缺失或不等；跳跃间断点是左右极限存在但不等；无穷间断点则至少一侧极限发散。

                证明题中不要只写图像直觉，应回到定义或定理条件。闭区间连续函数可以使用最值定理、介值定理和零点定理，开区间或半开区间则要检查条件是否完整。

                ## 三、一元函数微分学

                导数的几何意义是切线斜率，物理意义是瞬时变化率。可导推出连续，但连续不能推出可导。尖点、角点、垂直切线和振荡都可能造成不可导。参数方程和隐函数求导要明确自变量，不能把中间变量当作常数。

                单调性由导数符号控制，极值需要一阶导变号或结合二阶导判断。拐点关注凹凸性变化，不等同于二阶导为零。二阶导为零只是候选点，还要观察符号变化。

                ## 四、中值定理与不等式证明

                罗尔定理、拉格朗日中值定理和柯西中值定理的共同前提是闭区间连续、开区间可导。构造辅助函数时要让端点条件服务于目标式。证明不等式时，可以考虑把要证的不等式转化为函数单调性，或用泰勒公式控制余项符号。

                常见失误是直接套定理但没有说明区间连续和可导，或者把端点相等条件套到不满足罗尔定理的函数上。材料中如果出现“至少存在一点”，通常优先联想中值定理或零点定理。

                ## 五、积分计算方法

                不定积分先识别结构。换元法适合复合函数，分部积分适合乘积结构，尤其是多项式乘指数、三角、对数或反三角函数。分部积分选择 u 时，可以按“反对幂三指”的经验，但最终仍以求导后变简单为准。

                定积分除了计算，还要观察对称性、周期性、区间替换和几何意义。遇到绝对值、分段函数和含参积分，要先拆区间。反常积分必须写成极限形式，并先判断收敛性。

                ## 六、多元函数微分

                偏导数存在不代表函数连续，可微则一定连续且偏导存在。多元函数极值需要先求驻点，再结合 Hessian 矩阵或定义判断。条件极值常用拉格朗日乘数法，但约束条件的梯度不能为零，否则需要另行讨论。

                复合函数求偏导要画清变量依赖关系。全微分题中最容易漏掉间接依赖项，尤其是 z=f(x,y)、x=x(t)、y=y(t) 这种链式结构。

                ## 七、二重积分与变量替换

                二重积分首先画区域。直角坐标下要确定积分次序和上下限，极坐标适合圆、扇形、环形或含 x^2+y^2 的表达式。换元时必须乘以雅可比行列式的绝对值。

                交换积分次序时不要机械翻转上下限，应重新描述积分区域。若区域由多条曲线围成，必要时拆成多个子区域。奇偶性和对称性在二重积分中仍然有效，但要同时检查区域和被积函数。

                ## 八、无穷级数

                正项级数常用比较判别法、比值判别法和根值判别法。交错级数要检查项是否单调趋于零。幂级数需要求收敛半径，再单独判断端点。

                易错点是只求出收敛半径就停止，没有检查端点；或者把条件收敛误写成绝对收敛。函数展开成幂级数时，要从已知展开式出发，通过积分、求导或变量替换得到目标式，并说明收敛区间。

                ## 九、综合题审题提示

                强化阶段做综合题时，先标出题目要求：求值、证明、讨论还是应用。含参数题要把参数条件写完整，分段讨论时避免遗漏等号。答案形成后，用量纲、特殊值或极端情况做一次快速检查。

                错题复盘不要只写正确答案，应记录误用的定理、漏掉的条件和下一次识别信号。例如“加减结构慎用等价替换”“闭区间条件先检查”“二阶导为零只是候选点”等。
                """;
    }

    private String largeEnglishReview() {
        return """
                # 英语阅读强化课笔记与题型归纳

                ## 一、阅读流程

                阅读题不建议先精翻全文。更稳妥的流程是先看题干，圈出人名、年代、专有名词、态度词和逻辑词，再回到原文定位。定位后至少读定位句、前一句和后一句，判断作者真正强调的信息。

                选项对比时要关注范围、对象、情感色彩和逻辑关系。正确选项通常是原文的同义改写，不一定出现原词。干扰项往往利用原文词汇拼贴制造熟悉感。

                ## 二、主旨题

                主旨题优先看文章首段、各段首尾句、转折后的总结句和反复出现的概念。标题型主旨要覆盖全文，不宜过窄；段落主旨要服务于该段的论证功能，不要被例子细节牵走。

                常见错误是把作者举例说明的对象当成文章中心。例子通常是证据，中心往往在例子前后的概括句中。若选项只描述某个案例，通常范围偏窄。

                ## 三、细节题

                细节题要严格回文定位。选项如果更换了比较对象、扩大了时间范围、增加了绝对化词语，就要警惕。especially、mainly、only、never、always 等词会改变判断强度。

                不要用生活常识替代原文证据。考研阅读的正确答案必须被文本支撑，即使某个选项现实中合理，只要原文没有表达，也不能选。

                ## 四、推断题

                推断题不是自由发挥，而是在原文信息基础上的低强度延伸。答案通常不会跳出原文语境。含 infer、suggest、imply 的题目，要优先找因果、转折和评价表达。

                干扰项常见问题包括推断过度、无中生有和方向相反。若选项需要补充太多外部前提才能成立，应排除。

                ## 五、态度题

                态度题关注形容词、副词、情态动词、引号、让步和转折。作者可能先让步承认某观点，再在 however 后表达真正立场。neutral、critical、skeptical、optimistic 等词要精准区分。

                如果全文只是客观介绍，不要强行选择强烈情绪词。若作者通过 problem、concern、risk、fail 等词表达保留意见，可以考虑 skeptical 或 critical。

                ## 六、词义句意题

                词义题看上下文，而不是只看词典义。代词题要回到前文寻找最近且语义匹配的指代对象。句意题要把句子放回段落逻辑中，判断它承担解释、转折、例证还是总结功能。

                遇到熟词僻义时，先观察搭配和语境。选项若只对应单词常见义但不适合上下文，应排除。

                ## 七、例证题

                例证题的核心是“例子证明什么”。答案一般对应例子前后的观点句，而不是例子内部细节。看到 for example、such as、case、study 等信号时，要向前后寻找概括性表达。

                例子若位于段首，可能引出后文观点；若位于段中或段末，多半支撑前文观点。做题时先判断例子在段落中的位置。

                ## 八、长难句处理

                长难句先找谓语，再确定主干。定语从句、状语从句、同位语、插入语和非谓语结构可以暂时括起来。翻译时不要逐词硬排，应先保证逻辑清晰，再调整中文语序。

                多重修饰结构中，介词短语最容易造成误连。看到 of、with、by、in which 等结构时，要判断它修饰的是最近名词还是整句话。

                ## 九、错因复盘

                阅读错因建议按题型记录：定位错误、同义替换没识别、范围扩大、因果倒置、态度判断反向、被例子细节带偏。每道错题至少写一句“下次识别信号”，把经验转化为可执行提醒。

                强化阶段可以每三天做一次错因统计，观察哪些题型反复失误。若细节题错得多，优先训练定位和选项拆分；若主旨题错得多，优先训练段落功能和全文结构。
                """;
    }

    private String largePoliticsReview() {
        return """
                # 政治马原原理体系与材料题模板

                ## 一、唯物论

                世界的物质统一性要求从客观实际出发，坚持一切从实际出发、实事求是。意识具有能动作用，但意识不能脱离物质条件凭空创造现实。材料中出现规划、目标、精神动力时，要同时看到主观能动性和客观规律。

                规律具有客观性，人们不能创造、消灭或改造规律，但可以认识和利用规律。常见误区是把发挥主观能动性理解为随意突破客观条件。

                ## 二、辩证法总特征

                联系具有普遍性、客观性、多样性和条件性。发展是前进性与曲折性的统一，是新事物代替旧事物的过程。材料中出现系统治理、协同推进、阶段性困难时，可以联系普遍联系和发展的观点。

                分析发展问题要避免静止、孤立和片面的看法。既要看到趋势，也要看到过程中的矛盾和反复。

                ## 三、三大规律

                对立统一规律是唯物辩证法的实质和核心。矛盾具有普遍性和特殊性，要求承认矛盾、分析矛盾，并坚持具体问题具体分析。主要矛盾决定事物发展进程，矛盾主要方面决定事物性质。

                质量互变规律说明量变是质变的必要准备，质变是量变的必然结果。否定之否定规律揭示发展道路的前进性和曲折性统一。

                ## 四、认识论

                实践是认识的来源、动力、目的和检验标准。认识从实践到认识，再由认识回到实践，是不断反复和无限发展的过程。真理具有客观性，同时具有绝对性和相对性。

                感性认识和理性认识相互依赖。感性认识有待上升为理性认识，理性认识必须回到实践接受检验。材料题如果强调调研、试点、反馈和推广，通常可以联系实践认识循环。

                ## 五、历史唯物主义

                社会存在决定社会意识，社会意识具有相对独立性。生产力和生产关系、经济基础和上层建筑的矛盾运动推动社会发展。人民群众是历史的创造者。

                材料中出现制度改革、技术进步、群众参与和民生改善时，可以从社会基本矛盾和人民主体地位展开。不要只背概念，要把原理和材料关键词对应起来。

                ## 六、政治经济学基础

                商品具有使用价值和价值。价值由凝结在商品中的无差别人类劳动形成，交换价值是价值的表现形式。货币的职能包括价值尺度、流通手段、贮藏手段、支付手段和世界货币。

                剩余价值理论要把劳动力商品、必要劳动时间、剩余劳动时间和资本主义生产过程联系起来。不要把利润来源简单理解为流通中的买贱卖贵。

                ## 七、材料题答题结构

                材料题可以采用“原理表述、材料对应、方法论总结”的结构。第一步写出准确原理，第二步抓材料关键词解释对应关系，第三步说明应怎样做。答案要避免只堆概念，不回应材料。

                如果题目要求“分析体现了什么原理”，应优先找材料中的矛盾、联系、发展、实践、群众等信号。如果要求“如何做”，方法论部分要更具体。

                ## 八、易混点

                主要矛盾和矛盾主要方面不能混用。前者是在多个矛盾中找支配矛盾，后者是在同一矛盾双方中找决定性质的一方。量变质变不能写成量变必然立刻质变，必须强调量变积累到一定程度。

                实践标准的唯一性不等于一次实践就能穷尽真理。真理需要在实践中不断检验、发展和完善。

                ## 九、近期复盘建议

                每次做选择题后，把错项对应到原理。材料题复盘时，至少整理一个“材料关键词到原理”的映射。例如“试点推广”对应实践认识循环，“协同治理”对应普遍联系，“抓关键环节”对应主要矛盾。
                """;
    }

    private String largeCsReview() {
        return """
                # 408操作系统与网络综合复盘

                ## 一、进程与线程

                进程是资源分配的基本单位，线程是处理机调度的基本单位。同一进程内线程共享地址空间和打开文件等资源，但线程拥有自己的程序计数器、寄存器和栈。进程切换开销通常大于线程切换。

                进程状态转换包括就绪、运行、阻塞等。阻塞是因为等待事件或资源，不是因为时间片用完。时间片用完通常从运行态回到就绪态。

                ## 二、处理机调度

                先来先服务实现简单但可能产生 convoy effect。短作业优先平均等待时间较低，但可能导致长作业饥饿。时间片轮转适合分时系统，时间片过大退化为先来先服务，过小会增加上下文切换开销。

                优先级调度要区分抢占式和非抢占式。为了防止低优先级进程长期等待，可以采用老化机制逐步提高等待进程优先级。

                ## 三、同步与互斥

                临界区问题要求互斥、空闲让进、有限等待和让权等待。信号量 P 操作通常表示申请资源或进入临界区，V 操作表示释放资源或唤醒等待进程。互斥信号量初值通常为 1，资源信号量初值通常为资源数量。

                生产者消费者、读者写者和哲学家进餐问题要先识别共享资源，再确定互斥关系和同步关系。常见错误是 P/V 顺序写反，导致死锁或越界访问。

                ## 四、死锁

                死锁产生的四个必要条件是互斥、不可剥夺、请求并保持、循环等待。预防死锁是破坏必要条件，避免死锁是在资源分配前判断安全性，银行家算法属于避免死锁。

                资源分配图中出现环不一定必然死锁，若每类资源只有一个实例，环是死锁的充分必要条件；若资源有多个实例，环只是可能死锁。

                ## 五、内存管理

                连续分配方式包括首次适应、最佳适应和最坏适应。分页管理解决外部碎片，但可能存在内部碎片。分段管理便于程序逻辑共享和保护，但会产生外部碎片。段页式结合二者特点。

                地址转换题要区分页号、页内偏移、页表项和物理块号。快表命中时可以减少访问内存次数，缺页中断则需要操作系统介入调页。

                ## 六、虚拟内存

                虚拟内存基于局部性原理。页面置换算法包括最佳置换、先进先出、最近最久未使用和时钟算法。FIFO 可能出现 Belady 异常，LRU 通常需要记录最近访问情况。

                工作集模型用于控制驻留集大小，抖动通常是因为分配给进程的物理块过少，导致频繁缺页。

                ## 七、文件系统

                文件逻辑结构包括无结构字节流和有结构记录式文件。文件目录用于管理文件名和文件控制块。索引分配支持随机访问，链接分配不适合高效随机访问。

                位示图常用于空闲块管理。计算题要注意从字号、位号到磁盘块号的映射，不要混淆从 0 开始还是从 1 开始编号。

                ## 八、TCP 可靠传输

                TCP 通过序号、确认、超时重传、滑动窗口和校验和实现可靠传输。流量控制关注接收方缓存，拥塞控制关注网络负载。三次握手建立连接，四次挥手释放连接。

                TIME_WAIT 的意义是保证最后一个 ACK 丢失时仍可重传，并让旧连接报文在网络中消失。不要把 TIME_WAIT 误解为服务器独有状态。

                ## 九、拥塞控制

                慢开始阶段拥塞窗口指数增长，达到慢开始门限后进入拥塞避免，窗口线性增长。发生超时通常把门限调整为当前窗口一半，拥塞窗口降为初始值。收到三个重复确认时触发快重传和快恢复。

                拥塞窗口和接收窗口共同限制发送窗口，实际发送窗口取二者较小值。计算题要明确单位是 MSS、字节还是报文段。

                ## 十、综合复盘

                408 复盘建议把“概念、条件、算法步骤、计算公式、易错边界”分开整理。操作系统题重在状态和资源关系，网络题重在协议层次、报文交互和窗口变化。做错题时要写清楚错在概念判断、步骤遗漏还是单位换算。
                """;
    }

    private StudyFile saveDemoFile(User user, DemoFile demoFile) throws IOException {
        Files.createDirectories(uploadDir.resolve(String.valueOf(user.getId())).resolve(String.valueOf(demoFile.folder().getId())));
        Path target = uploadDir.resolve(String.valueOf(user.getId()))
                .resolve(String.valueOf(demoFile.folder().getId()))
                .resolve("demo-" + demoFile.name());
        Files.writeString(target, demoFile.content());

        StudyFile file = new StudyFile();
        file.setFolder(demoFile.folder());
        file.setTag(demoFile.tag());
        file.setOriginalName(demoFile.name());
        file.setStoredPath(target.toString());
        file.setContentType("text/markdown");
        file.setExtractedText(demoFile.content());
        file.setKnowledgeEnabled(true);
        return fileRepository.save(file);
    }

    private List<KnowledgeChunk> saveChunks(StudyFile file, String subjectKey, Map<String, Integer> subjectCounters) {
        String[] sections = file.getExtractedText().split("\\n\\s*\\n");
        List<KnowledgeChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;
        StringBuilder pending = new StringBuilder();
        for (String section : sections) {
            String trimmed = section.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (!pending.isEmpty()) {
                pending.append("\n\n");
            }
            pending.append(trimmed);
            if (pending.length() >= 120) {
                int subjectIndex = subjectCounters.merge(subjectKey, 1, Integer::sum) - 1;
                chunks.add(saveChunk(file, chunkIndex++, subjectKey, subjectIndex, pending.toString()));
                pending.setLength(0);
            }
        }
        if (!pending.isEmpty()) {
            int subjectIndex = subjectCounters.merge(subjectKey, 1, Integer::sum) - 1;
            chunks.add(saveChunk(file, chunkIndex, subjectKey, subjectIndex, pending.toString()));
        }
        return chunks;
    }

    private KnowledgeChunk saveChunk(StudyFile file, int chunkIndex, String subjectKey, int subjectIndex, String content) {
        ChunkStat stat = statFor(subjectKey, subjectIndex);
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setFile(file);
        chunk.setFolder(file.getFolder());
        chunk.setChunkIndex(chunkIndex);
        chunk.setPageNumber(Math.max(1, chunkIndex + 1));
        chunk.setChunkingVersion(CHUNKING_VERSION);
        chunk.setContent(content);
        chunk.setCiteCount(stat.citeCount());
        chunk.setCorrectHitCount(stat.correctCount());
        chunk.setWrongHitCount(stat.wrongCount());
        chunk.setLastAccessedAt(stat.lastAccessedAt());
        chunk.setLastPracticedAt(stat.lastPracticedAt());
        return chunkRepository.save(chunk);
    }

    private ChunkStat statFor(String subjectKey, int index) {
        LocalDate recentBase = LocalDate.now().minusDays(index % 18L);
        return switch (subjectKey) {
            case "math" -> new ChunkStat(4 + index % 4, 1 + index % 2, 3 + index % 5,
                    at(recentBase), at(recentBase.minusDays(1)));
            case "english" -> index % 4 == 0
                    ? new ChunkStat(0, 0, 1 + index % 2, at(ACTIVITY_START_DATE.plusDays(index % 20L)), null)
                    : new ChunkStat(3 + index % 3, 2 + index % 3, 2 + index % 4,
                            at(recentBase.minusDays(3)), at(recentBase.minusDays(5)));
            case "politics" -> index % 3 == 0
                    ? new ChunkStat(0, 0, index % 2, at(ACTIVITY_START_DATE.plusDays(index % 16L)), null)
                    : new ChunkStat(1 + index % 2, 3 + index % 4, 1 + index % 3,
                            at(ACTIVITY_START_DATE.plusDays(4L + index)), at(ACTIVITY_START_DATE.plusDays(2L + index)));
            case "cs" -> index % 5 == 0
                    ? new ChunkStat(0, 0, 2 + index % 3, at(recentBase.minusDays(9)), null)
                    : new ChunkStat(2 + index % 3, 4 + index % 4, 4 + index % 5,
                            at(recentBase.minusDays(2)), at(recentBase.minusDays(8)));
            default -> new ChunkStat(1, 1, 1, at(recentBase), at(recentBase));
        };
    }

    private Instant at(LocalDate date) {
        return date.atTime(20, 0).atZone(ZoneId.systemDefault()).toInstant();
    }

    private void createKnowledgeEvents(User user, List<KnowledgeChunk> chunks) {
        int day = 0;
        for (KnowledgeChunk chunk : chunks) {
            for (int i = 0; i < chunk.getCiteCount(); i++) {
                saveChunkEvent(user, chunk, KnowledgeChunkEventType.CITED, null, ACTIVITY_START_DATE.plusDays(day % 32L).atTime(19, 20));
                day++;
            }
            for (int i = 0; i < chunk.getCorrectHitCount(); i++) {
                saveChunkEvent(user, chunk, KnowledgeChunkEventType.PRACTICE_CORRECT, true, ACTIVITY_START_DATE.plusDays(day % 32L).atTime(20, 10));
                day++;
            }
            for (int i = 0; i < chunk.getWrongHitCount(); i++) {
                saveChunkEvent(user, chunk, KnowledgeChunkEventType.PRACTICE_WRONG, false, ACTIVITY_START_DATE.plusDays(day % 32L).atTime(20, 45));
                day++;
            }
        }
        createRecentKnowledgeEvents(user, chunks);
    }

    private void createRecentKnowledgeEvents(User user, List<KnowledgeChunk> chunks) {
        int eventCount = Math.min(chunks.size(), 32);
        for (int i = 0; i < eventCount; i++) {
            KnowledgeChunk chunk = chunks.get((i * 5) % chunks.size());
            LocalDate activityDate = LocalDate.now().minusDays(i % 9L);
            if (i % 4 == 0) {
                chunk.setCiteCount(chunk.getCiteCount() + 1);
                saveChunkEvent(user, chunk, KnowledgeChunkEventType.CITED, null, activityDate.atTime(18, 40));
            } else {
                boolean correct = i % 5 != 0;
                if (correct) {
                    chunk.setCorrectHitCount(chunk.getCorrectHitCount() + 1);
                    saveChunkEvent(user, chunk, KnowledgeChunkEventType.PRACTICE_CORRECT, true, activityDate.atTime(20, 15));
                } else {
                    chunk.setWrongHitCount(chunk.getWrongHitCount() + 1);
                    saveChunkEvent(user, chunk, KnowledgeChunkEventType.PRACTICE_WRONG, false, activityDate.atTime(20, 45));
                }
                chunk.setLastPracticedAt(at(activityDate));
            }
            chunk.setLastAccessedAt(at(activityDate));
            chunkRepository.save(chunk);
        }
    }

    private void saveChunkEvent(User user, KnowledgeChunk chunk, KnowledgeChunkEventType type, Boolean correct, java.time.LocalDateTime createdAt) {
        KnowledgeChunkEvent event = new KnowledgeChunkEvent();
        event.setOwnerId(user.getId());
        event.setChunkId(chunk.getId());
        event.setFileId(chunk.getFile().getId());
        event.setFolderId(chunk.getFolder().getId());
        event.setEventType(type);
        event.setCorrect(correct);
        event.setCreatedAt(createdAt.atZone(ZoneId.systemDefault()).toInstant());
        chunkEventRepository.save(event);
    }

    private List<MistakeQuestion> createMistakes(User user,
                                                 Map<String, MistakeSubjectTag> tags,
                                                 Map<String, MistakeStatus> statuses,
                                                 List<KnowledgeChunk> chunks) {
        List<MistakeQuestion> mistakes = List.of(
                mistake(user, statuses.get("公式不熟"), false, "求极限 lim(x->0) (1-cos x)/x^2，并说明能否直接使用等价无穷小。", "答案为 1/2。因为 1-cos x 等价于 x^2/2，所以商的极限为 1/2；也可以用洛必达法则验证。", tags, List.of("考研数学")),
                mistake(user, statuses.get("概念混淆"), false, "齐次线性方程组 Ax=0 在什么条件下存在非零解？", "当 r(A) 小于未知量个数 n 时存在非零解；若 r(A)=n，则只有零解。", tags, List.of("考研数学")),
                mistake(user, statuses.get("概念混淆"), false, "概率论中为什么不能把互斥事件直接当成独立事件？", "互斥强调不能同时发生，独立强调一个事件发生不影响另一个事件概率。两个非零概率事件互斥时通常不独立。", tags, List.of("考研数学")),
                mistake(user, statuses.get("已订正"), true, "英语阅读中，为什么不能只看定位句本身就选答案？", "定位句通常只提供局部信息，需要结合上下句判断逻辑、态度和同义替换，避免落入词汇拼贴干扰项。", tags, List.of("考研英语")),
                mistake(user, statuses.get("方法迁移"), false, "例证题应该优先看例子本身还是例子服务的观点？", "应优先看例子服务的观点。例子是为了证明前后概括句，答案通常对应观点而不是例子细节。", tags, List.of("考研英语")),
                mistake(user, statuses.get("审题失误"), false, "长难句翻译时主干和修饰成分的处理顺序是什么？", "先找谓语和主干，再处理从句、非谓语和插入语，最后按中文逻辑重组。", tags, List.of("考研英语")),
                mistake(user, statuses.get("概念混淆"), false, "材料题中如何区分主要矛盾和矛盾的主要方面？", "主要矛盾强调多个矛盾中哪一个起支配作用；矛盾主要方面强调同一矛盾双方中哪一方面决定事物性质。", tags, List.of("考研政治")),
                mistake(user, statuses.get("未掌握"), false, "实践为什么是检验认识真理性的唯一标准？", "因为认识是否正确反映客观对象，必须通过实践结果来检验，不能只靠主观感觉或理论自洽。", tags, List.of("考研政治")),
                mistake(user, statuses.get("二刷重点"), false, "史纲会议题为什么要放回历史背景复习？", "会议的内容和意义往往与当时主要矛盾、革命任务有关，脱离背景容易混淆时间和作用。", tags, List.of("考研政治")),
                mistake(user, statuses.get("二刷重点"), false, "已知先序和后序遍历序列，是否一定能唯一确定二叉树？", "不一定。通常需要中序序列配合先序或后序，才能唯一确定一棵二叉树。", tags, List.of("408计算机基础")),
                mistake(user, statuses.get("需要二刷"), false, "死锁产生的四个必要条件是什么？如何预防？", "四个条件是互斥、不可剥夺、请求并保持、循环等待。预防死锁可以破坏其中任意一个条件。", tags, List.of("408计算机基础")),
                mistake(user, statuses.get("已订正"), true, "时间片轮转中时间片过大或过小分别有什么问题？", "时间片过大会退化为先来先服务；过小会导致上下文切换频繁，系统开销增加。", tags, List.of("408计算机基础")),
                mistake(user, statuses.get("概念混淆"), false, "TCP 流量控制和拥塞控制的关注点有什么区别？", "流量控制关注接收方缓存能力，拥塞控制关注网络整体承载能力。", tags, List.of("408计算机基础")),
                mistake(user, statuses.get("公式不熟"), false, "为什么 sin x - x 这类加减结构不能直接用等价无穷小把 sin x 替换成 x？", "加减结构会抵消低阶项，直接替换会丢失主导项。应使用泰勒展开到三阶，sin x - x 等价于 -x^3/6。", tags, List.of("考研数学")),
                mistake(user, statuses.get("概念混淆"), false, "二阶导数等于 0 是否一定是拐点？", "不一定。二阶导为 0 只是候选条件，还要检查凹凸性是否在该点两侧发生变化。", tags, List.of("考研数学")),
                mistake(user, statuses.get("审题失误"), false, "二重积分换元时最容易漏掉哪一步？", "必须乘以雅可比行列式的绝对值，并重新确认变量替换后的积分区域。", tags, List.of("考研数学")),
                mistake(user, statuses.get("方法迁移"), false, "英语主旨题为什么不能选择只描述某个案例的选项？", "主旨题要求覆盖全文或整段论证中心，案例通常只是证据。只描述案例的选项范围偏窄。", tags, List.of("考研英语")),
                mistake(user, statuses.get("审题失误"), false, "推断题中，什么样的选项属于推断过度？", "如果选项需要补充多个外部前提，或结论明显跳出原文语境，即使现实合理，也属于推断过度。", tags, List.of("考研英语")),
                mistake(user, statuses.get("方法迁移"), false, "政治材料题中“试点、反馈、推广”通常对应哪个认识论链条？", "通常对应实践到认识、再由认识回到实践的循环发展过程，强调实践是认识来源、动力、目的和检验标准。", tags, List.of("考研政治")),
                mistake(user, statuses.get("概念混淆"), false, "主要矛盾和矛盾主要方面的答题关键词如何区分？", "主要矛盾看多个矛盾中哪一个起支配作用；矛盾主要方面看同一矛盾双方中哪一方决定事物性质。", tags, List.of("考研政治")),
                mistake(user, statuses.get("概念混淆"), false, "进程阻塞和时间片用完导致的状态转换有什么不同？", "阻塞是运行态因等待事件或资源进入阻塞态；时间片用完通常是运行态回到就绪态。", tags, List.of("408计算机基础")),
                mistake(user, statuses.get("二刷重点"), false, "资源分配图中出现环是否一定说明死锁？", "不一定。每类资源只有一个实例时，环是死锁的充分必要条件；资源有多个实例时，环只表示可能死锁。", tags, List.of("408计算机基础")),
                mistake(user, statuses.get("审题失误"), false, "TCP 实际发送窗口由哪些窗口共同限制？", "实际发送窗口通常取拥塞窗口和接收窗口中的较小值，计算时要注意单位是 MSS、字节还是报文段。", tags, List.of("408计算机基础"))
        );

        List<MistakeQuestion> saved = new ArrayList<>();
        for (int i = 0; i < mistakes.size(); i++) {
            MistakeQuestion mistake = mistakeRepository.save(mistakes.get(i));
            saved.add(mistake);
            KnowledgeChunk linkedChunk = chunks.get(i % chunks.size());
            MistakeQuestionChunk relation = new MistakeQuestionChunk();
            relation.setMistake(mistake);
            relation.setChunk(linkedChunk);
            relation.setSourceType(i % 2 == 0 ? MistakeQuestionChunkSourceType.MANUAL : MistakeQuestionChunkSourceType.TEACHER);
            mistakeChunkRepository.save(relation);
        }
        return saved;
    }

    private MistakeQuestion mistake(User user,
                                    MistakeStatus status,
                                    boolean mastered,
                                    String question,
                                    String solution,
                                    Map<String, MistakeSubjectTag> allTags,
                                    List<String> tagNames) {
        MistakeQuestion mistake = new MistakeQuestion();
        mistake.setOwner(user);
        mistake.setStatus(status);
        mistake.setMastered(mastered);
        mistake.setQuestionText(question);
        mistake.setSolutionText(solution);
        Set<MistakeSubjectTag> subjectTags = new LinkedHashSet<>();
        for (String name : tagNames) {
            MistakeSubjectTag tag = allTags.get(name);
            if (tag != null) {
                subjectTags.add(tag);
            }
        }
        mistake.setSubjectTags(subjectTags);
        return mistake;
    }

    private void createMistakeEvents(User user, List<MistakeQuestion> mistakes) {
        for (int i = 0; i < mistakes.size(); i++) {
            for (int attempt = 0; attempt < 3; attempt++) {
                MistakePracticeEvent event = new MistakePracticeEvent();
                event.setOwnerId(user.getId());
                event.setMistakeId(mistakes.get(i).getId());
                event.setCorrect(mistakes.get(i).isMastered() || (i + attempt) % 4 == 0);
                event.setCreatedAt(ACTIVITY_START_DATE.plusDays((i * 3L + attempt) % 32L)
                        .atTime(21, 10)
                        .atZone(ZoneId.systemDefault())
                        .toInstant());
                practiceEventRepository.save(event);
            }
        }
        createRecentMistakeEvents(user, mistakes);
    }

    private void createRecentMistakeEvents(User user, List<MistakeQuestion> mistakes) {
        int eventCount = Math.min(mistakes.size(), 20);
        for (int i = 0; i < eventCount; i++) {
            MistakePracticeEvent event = new MistakePracticeEvent();
            event.setOwnerId(user.getId());
            event.setMistakeId(mistakes.get(i).getId());
            event.setCorrect(mistakes.get(i).isMastered() || i % 3 == 0);
            event.setCreatedAt(LocalDate.now().minusDays(i % 8L)
                    .atTime(21, 35)
                    .atZone(ZoneId.systemDefault())
                    .toInstant());
            practiceEventRepository.save(event);
        }
    }

    private void createStudyPlan(User user) {
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        List<PlanSeed> plans = List.of(
                new PlanSeed(-4, "4月下旬基础复盘启动", "综合", "建立错题状态，完成第一轮资料导入", StudyPlanItemType.TASK, LocalTime.of(19, 0), LocalTime.of(20, 30), "图书馆", StudyPlanPriority.MEDIUM, StudyPlanStatus.DONE, StudyPlanSource.MANUAL),
                new PlanSeed(-3, "高数极限与导数专题", "考研数学", "极限、连续、导数三章框架复盘", StudyPlanItemType.REVIEW, LocalTime.of(8, 30), LocalTime.of(10, 30), "自习室 A201", StudyPlanPriority.HIGH, StudyPlanStatus.DONE, StudyPlanSource.AI),
                new PlanSeed(-3, "英语阅读错因归类", "考研英语", "整理主旨题、例证题、态度题错因", StudyPlanItemType.TASK, LocalTime.of(19, 30), LocalTime.of(21, 0), "图书馆", StudyPlanPriority.MEDIUM, StudyPlanStatus.DONE, StudyPlanSource.MANUAL),
                new PlanSeed(-2, "政治马原原理背诵", "考研政治", "矛盾分析法、实践认识论、真理观", StudyPlanItemType.REVIEW, LocalTime.of(20, 0), LocalTime.of(21, 10), "宿舍", StudyPlanPriority.MEDIUM, StudyPlanStatus.DONE, StudyPlanSource.MANUAL),
                new PlanSeed(-2, "408 操作系统同步", "408计算机基础", "P/V 操作、死锁条件、银行家算法", StudyPlanItemType.SELF_STUDY, LocalTime.of(9, 0), LocalTime.of(10, 40), "机房", StudyPlanPriority.HIGH, StudyPlanStatus.SKIPPED, StudyPlanSource.AI),
                new PlanSeed(-1, "线代矩阵秩专题", "考研数学", "完成 20 道秩与方程组题", StudyPlanItemType.TASK, LocalTime.of(9, 0), LocalTime.of(10, 30), "自习室 A201", StudyPlanPriority.HIGH, StudyPlanStatus.DONE, StudyPlanSource.AI),
                new PlanSeed(-1, "计算机网络 TCP 复盘", "408计算机基础", "三次握手、四次挥手和拥塞控制", StudyPlanItemType.REVIEW, LocalTime.of(15, 0), LocalTime.of(16, 30), "机房", StudyPlanPriority.HIGH, StudyPlanStatus.DONE, StudyPlanSource.MANUAL),
                new PlanSeed(0, "高数积分方法归纳", "考研数学", "换元、分部积分、对称性", StudyPlanItemType.SELF_STUDY, LocalTime.of(8, 30), LocalTime.of(10, 0), "图书馆", StudyPlanPriority.HIGH, StudyPlanStatus.TODO, StudyPlanSource.MANUAL),
                new PlanSeed(0, "英语作文图表段落", "考研英语", "背诵 6 个趋势表达并写一段", StudyPlanItemType.TASK, LocalTime.of(19, 30), LocalTime.of(20, 20), "图书馆", StudyPlanPriority.MEDIUM, StudyPlanStatus.TODO, StudyPlanSource.MANUAL),
                new PlanSeed(1, "数据结构树与图", "408计算机基础", "二叉树遍历和拓扑排序专项", StudyPlanItemType.REVIEW, LocalTime.of(14, 0), LocalTime.of(15, 40), "自习室 A201", StudyPlanPriority.HIGH, StudyPlanStatus.TODO, StudyPlanSource.AI),
                new PlanSeed(1, "政治选择题 50 题", "考研政治", "记录易混原理和关键词", StudyPlanItemType.EXAM, LocalTime.of(20, 0), LocalTime.of(21, 10), "宿舍", StudyPlanPriority.MEDIUM, StudyPlanStatus.TODO, StudyPlanSource.MANUAL),
                new PlanSeed(2, "英语阅读限时训练", "考研英语", "4 篇阅读，控制每篇 18 分钟", StudyPlanItemType.EXAM, LocalTime.of(9, 0), LocalTime.of(11, 0), "图书馆", StudyPlanPriority.HIGH, StudyPlanStatus.TODO, StudyPlanSource.MANUAL),
                new PlanSeed(2, "408 周测复盘", "408计算机基础", "复盘数据结构、操作系统和网络错题", StudyPlanItemType.EXAM, LocalTime.of(15, 0), LocalTime.of(17, 0), "机房", StudyPlanPriority.HIGH, StudyPlanStatus.TODO, StudyPlanSource.AI),
                new PlanSeed(3, "概率论随机变量专项", "考研数学", "分布函数、期望方差、常见分布", StudyPlanItemType.SELF_STUDY, LocalTime.of(8, 30), LocalTime.of(10, 0), "自习室 A201", StudyPlanPriority.MEDIUM, StudyPlanStatus.TODO, StudyPlanSource.AI),
                new PlanSeed(3, "政治史纲时间线", "考研政治", "重要会议和历史任务串联", StudyPlanItemType.REVIEW, LocalTime.of(20, 0), LocalTime.of(21, 0), "宿舍", StudyPlanPriority.LOW, StudyPlanStatus.TODO, StudyPlanSource.MANUAL),
                new PlanSeed(4, "周总结与下周规划", "综合", "查看知识画像薄弱点，安排下周计划", StudyPlanItemType.REVIEW, LocalTime.of(19, 0), LocalTime.of(20, 30), "宿舍", StudyPlanPriority.MEDIUM, StudyPlanStatus.TODO, StudyPlanSource.AI)
        );

        for (PlanSeed seed : plans) {
            StudyPlanItem item = new StudyPlanItem();
            item.setOwner(user);
            item.setTitle(seed.title());
            item.setSubject(seed.subject());
            item.setDescription(seed.description());
            item.setItemType(seed.type());
            item.setStartDate(monday.plusWeeks(seed.weekOffset()).plusDays(Math.floorMod(seed.weekOffset(), 2)));
            item.setStartTime(seed.start());
            item.setEndTime(seed.end());
            item.setLocation(seed.location());
            item.setPriority(seed.priority());
            item.setStatus(seed.status());
            item.setSource(seed.source());
            planItemRepository.save(item);
        }
    }

    private void reindexDemoFiles(User user) {
        AiSettingsResponse settings = new AiSettingsResponse(
                "严谨的考研答疑老师",
                AiSettingsService.DEFAULT_SYSTEM_PROMPT,
                "gpt-4o-mini",
                "https://api.openai.com/v1/chat/completions",
                "",
                "text-embedding-v4",
                "https://api.openai.com/v1/embeddings",
                "",
                1536
        );
        for (StudyFile file : fileRepository.findByKnowledgeEnabledTrue()) {
            if (file.getFolder().getOwner().getId().equals(user.getId())) {
                elasticsearchService.reindexFile(user.getId(), file, chunkRepository.findByFileIdOrderByChunkIndexAsc(file.getId()), settings);
            }
        }
    }

    private record DemoFile(String subjectKey, StudyFolder folder, FileTag tag, String name, String content) {
    }

    private record ChunkStat(int correctCount, int wrongCount, int citeCount, Instant lastAccessedAt, Instant lastPracticedAt) {
    }

    private record PlanSeed(int weekOffset,
                            String title,
                            String subject,
                            String description,
                            StudyPlanItemType type,
                            LocalTime start,
                            LocalTime end,
                            String location,
                            StudyPlanPriority priority,
                            StudyPlanStatus status,
                            StudyPlanSource source) {
    }
}

package edu.utdallas.seers.lasso.data.entity;

import edu.utdallas.seers.retrieval.Query;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class PatternEntry implements Query {

    private final String context;
    private final String text;
    private final Map<String, Supplier<String>> fields = Map.of(
            "text", this::getText,
            "context", this::getContext
    );
    private final List<String> operands;
    private final String extra;
    public final String consequence;
    private String constraintId;
    private ConstraintType cType;
    private PatternTruth[] pTrus;
    private List<DetectorInput> inputs;
    private PatternType pType;
    private String system;

    public PatternEntry(String constraintId, PatternTruth[] pTrus, List<DetectorInput> inputs,
                        String system, ConstraintType constraintType, PatternType patternType,
                        String context, String text, List<String> operands, String consequence, String extra) {
        this.constraintId = constraintId;
        this.pTrus = pTrus;
        this.inputs = inputs;
        this.system = system;
        this.cType = constraintType;
        pType = patternType;
        this.context = context;
        this.text = text;
        this.operands = operands;
        this.consequence = consequence;
        this.extra = extra;
    }

//    public PatternOutputFormat toOutputFormat(List<Pattern> patterns) {
//        List<PatternSingleLineFormat> detected = new ArrayList<>();
//        // FIXME instead of converting each pattern to single line, let them have multiple lines if that is how they were detected
//        for (Pattern pattern : patterns) {
//            Optional<PatternSingleLineFormat> pslf = pattern.toSingleLineFormat();
//            pslf.ifPresent(detected::add);
//        }
//        Set<PatternSingleLineFormat> set = new HashSet<>(detected);
//        detected.clear();
//        detected.addAll(set);
//
//        List<PatternSingleLineFormat> falseNegatives = getgrdTruthinSingleLineFormat();
//        List<PatternSingleLineFormat> falsePositives = new ArrayList<>();
//        List<PatternSingleLineFormat> truePositives = new ArrayList<>();
//
//        for (PatternSingleLineFormat p : detected) {
//            boolean flag = false;
//            for (int i = 0; i < falseNegatives.size(); i++) {
//                PatternSingleLineFormat fn = falseNegatives.get(i);
//                if (p.getLineNum() == fn.getLineNum() &&
//                        p.getFile().equals(fn.getFile())) {
//                    flag = true;
//                    truePositives.add(p);
//                    falseNegatives.remove(i);
//                    break;
//                }
//            }
//            if (!flag) falsePositives.add(p);
//        }
//
//        // FIXME assumes there is only one ground truth
//        if (!truePositives.isEmpty()) {
//            falseNegatives = Collections.emptyList();
//        }
//
//        return new PatternOutputFormat(
//                this,
//                truePositives,
//                falsePositives,
//                falseNegatives);
//    }

    public List<PatternSingleLineFormat> getgrdTruthinSingleLineFormat() {
        List<PatternSingleLineFormat> ret = new ArrayList<>();
        for (PatternTruth t : pTrus) {
            ret.addAll(t.toSingleLineFormat(pType));
        }
        return ret;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        //    sb.append("entry point: ");
        //    sb.append(entryPoint);
        //    sb.append("\n");

        sb.append("ground truth: \n[");
        for (PatternTruth pt : pTrus) {
            sb.append(pt.toString());
        }
        sb.append("]\n");

        sb.append(inputs.toString());

        sb.append("pattern: ");

        sb.append(pType.toString().toLowerCase().replace("_", "-"));

        sb.append("]\n");

        sb.append("inputs: ");
        for (DetectorInput inp : inputs) {
            sb.append(inp);
        }

        return sb.toString();
    }

    @Override
    public List<String> getGroundTruthIDs() {
        return Arrays.stream(pTrus)
                .map(t -> t.getFile() + ":" +
                        Arrays.stream(t.getLines())
                                .mapToObj(Objects::toString)
                                .collect(Collectors.joining(","))
                )
                .collect(Collectors.toList());
    }

    @Override
    public String getID() {
        return constraintId;
    }

    @Override
    public String getField(String name) {
        return Objects.requireNonNull(fields.get(name))
                .get();
    }

    public static List<String> getFieldNames() {
        return Arrays.asList("text", "context");
    }

    public String getContext() {
        return context;
    }

    public String getConstraintId() {
        return constraintId;
    }

    public ConstraintType getcType() {
        return cType;
    }

    public PatternTruth[] getpTrus() {
        return pTrus;
    }

    public List<DetectorInput> getInputs() {
        return inputs;
    }

    public PatternType getpType() {
        return pType;
    }

    public String getSystem() {
        return system;
    }

    public String getText() {
        return text;
    }

    public List<String> getOperands() {
        return operands;
    }

    public Optional<String> getExtra() {
        return Optional.ofNullable(extra);
    }
}

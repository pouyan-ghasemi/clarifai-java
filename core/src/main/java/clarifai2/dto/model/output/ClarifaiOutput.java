package clarifai2.dto.model.output;

import clarifai2.dto.ClarifaiStatus;
import clarifai2.dto.HasClarifaiIDRequired;
import clarifai2.dto.input.ClarifaiInput;
import clarifai2.dto.model.Model;
import clarifai2.dto.model.ModelType;
import clarifai2.dto.prediction.Concept;
import clarifai2.dto.prediction.Focus;
import clarifai2.dto.prediction.Frame;
import clarifai2.dto.prediction.Prediction;
import clarifai2.internal.JSONAdapterFactory;
import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static clarifai2.internal.InternalUtil.assertJsonIs;
import static clarifai2.internal.InternalUtil.fromJson;
import static clarifai2.internal.InternalUtil.isJsonNull;

@SuppressWarnings("NullableProblems")
@AutoValue
@JsonAdapter(ClarifaiOutput.Adapter.class)
public abstract class ClarifaiOutput<PREDICTION extends Prediction> implements HasClarifaiIDRequired {

  ClarifaiOutput() {} // AutoValue instances only

  @NotNull public abstract Date createdAt();

  @NotNull public abstract Model<PREDICTION> model();

  @Nullable public abstract ClarifaiInput input();

  @NotNull public abstract List<PREDICTION> data();

  @NotNull public abstract ClarifaiStatus status();


  @SuppressWarnings("rawtypes")
  static class Adapter extends JSONAdapterFactory<ClarifaiOutput> {
    @Nullable @Override protected Deserializer<ClarifaiOutput> deserializer() {
      return new Deserializer<ClarifaiOutput>() {
        @Nullable @Override
        public ClarifaiOutput deserialize(
            @NotNull JsonElement json,
            @NotNull TypeToken<ClarifaiOutput> type,
            @NotNull Gson gson
        ) {
          final JsonObject root = assertJsonIs(json, JsonObject.class);

          final List<Prediction> allPredictions = new ArrayList<>();
          Class<? extends Prediction> predictionType =
              ModelType.determineModelType(root.getAsJsonObject("model").getAsJsonObject("output_info"))
                  .predictionType();

          if (!root.get("data").isJsonNull()) {
            JsonObject dataRoot = root.getAsJsonObject("data");

            // Video model is ambiguous coming out of API.
            if (predictionType == Concept.class && dataRoot.has("frames")) {
              predictionType = Frame.class;
            }

//          more hacky solutions. Will refactor this eventually.
            double value = 0.0;
            if (predictionType == Focus.class) {
              value = dataRoot.getAsJsonObject("focus")
                  .getAsJsonPrimitive("value")
                  .getAsFloat();
              dataRoot.remove("focus");
            }

            for (final Map.Entry<String, JsonElement> data : dataRoot.entrySet()) {
              final JsonArray array =
                  data.getValue().isJsonArray() ? data.getValue().getAsJsonArray() : new JsonArray();
              for (JsonElement predictionJSON : array) {
                if (predictionType == Focus.class) {
                  JsonObject addValue = predictionJSON.getAsJsonObject();
                  addValue.add("value", new JsonPrimitive(value));
                  predictionJSON = addValue;
                }
                allPredictions.add(fromJson(gson, predictionJSON, predictionType));
              }
            }
          }

          return new AutoValue_ClarifaiOutput<>(
              root.get("id").getAsString(),
              fromJson(gson, root.get("created_at"), Date.class),
              fromJson(gson, root.get("model"), new TypeToken<Model<Prediction>>() {}),
              isJsonNull(root.get("input")) ? null : fromJson(gson, root.get("input"), ClarifaiInput.class),
              allPredictions,
              fromJson(gson, root.get("status"), ClarifaiStatus.class)
          );
        }
      };
    }

    @NotNull @Override protected TypeToken<ClarifaiOutput> typeToken() {
      return new TypeToken<ClarifaiOutput>() {};
    }
  }
}

package bcntec.learning.bigdata.hadoop.mapred.partitioners;

import lombok.NoArgsConstructor;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author fphilip@bcntec.es
 */

@SuppressWarnings("unused")
public class WordCounterWithCombiner {


    public static void main(String[] args) {

        if (args.length != 2) {
            System.err.println("Use: WordCounterWithCombiner <SOURCE_PATH> <TARGET_PATH>");
            System.exit(1);
        }


        String source = args[0];
        String target = args[1];

        boolean success = false;

        try {
            Configuration conf = new Configuration();
            Job job = Job.getInstance(conf, "My WordCounterWithCombiner Program");
            job.setJarByClass(WordCounterWithCombiner.class);

            FileInputFormat.addInputPath(job, new Path(source));
            FileOutputFormat.setOutputPath(job, new Path(target));

            job.setMapperClass(WordCounterMap.class);
            job.setReducerClass(WorldCounterCombiner.class);
            job.setOutputKeyClass(Text.class);
            job.setOutputValueClass(LongWritable.class);
            job.setCombinerClass(WorldCounterCombiner.class);
            job.setNumReduceTasks(27);

            success = job.waitForCompletion(true);
        } catch (Exception e) {
            System.err.println("Error counting words.");
            e.printStackTrace(System.err);
        }

        System.exit(success ? 0 : 1);

    }


    @NoArgsConstructor
    public static class WordCounterMap extends Mapper<LongWritable, Text, Text, LongWritable> {


        @Override
        protected void map(LongWritable key, Text value, Mapper<LongWritable, Text, Text, LongWritable>.Context context) throws IOException, InterruptedException {

            String line = value.toString().toUpperCase().replaceAll("[^a-zA-Z 0-9]+", "").replaceAll("\\s+", " ");


            if (line != null && !"".equals(line)) {
                final String[] words = line.split(" ");

                List<String> finalWords = new ArrayList<>(words.length);
                for (String word : words) {
                    if (!"".equals(word)) {
                        finalWords.add(word);
                    }
                }

                for (String word : finalWords) {
                    context.write(new Text(word), new LongWritable(1));
                }
            }

        }


    }

    @NoArgsConstructor
    public final class WorldCounterCombiner extends Reducer<Text, LongWritable, Text, LongWritable> {


        @Override
        protected void reduce(Text key, Iterable<LongWritable> values, Reducer<Text, LongWritable, Text, LongWritable>.Context context) throws IOException, InterruptedException {

            long wordOccurrences = 0;

            for (LongWritable value : values) {
                wordOccurrences++;
            }

            context.write(key, new LongWritable(wordOccurrences));

        }
    }
}
package jeco.operator.comparator;

import java.util.ArrayList;
import java.util.Comparator;
import jeco.problem.Solution;
import jeco.problem.Variable;

public class SolutionDominance<T extends Variable<?>> implements Comparator<Solution<T>> {

  @Override
  public int compare(Solution<T> s1, Solution<T> s2) {
    if (s2 == null) {
      return -1;
    }
    int n = Math.min(s1.getObjectives().size(), s2.getObjectives().size());
    ArrayList<Double> z1 = s1.getObjectives();
    ArrayList<Double> z2 = s2.getObjectives();

    boolean bigger = false;
    boolean smaller = false;
    boolean indiff = false;
    for (int i = 0; !(indiff) && i < n; i++) {
      if (z1.get(i) > z2.get(i)) {
        bigger = true;
      }
      if (z1.get(i) < z2.get(i)) {
        smaller = true;
      }
      indiff = (bigger && smaller);
    }

    if (smaller && !bigger) {
      return -1;
    } else if (bigger && !smaller) {
      return 1;
    }
    return 0;
  }
}

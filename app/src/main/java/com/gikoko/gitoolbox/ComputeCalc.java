package com.gikoko.gitoolbox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** Calculator tools (25). */
final class ComputeCalc {

    private ComputeCalc() {
    }

    static String[] fl(String label, String hint) {
        return new String[]{label, hint};
    }

    static String[] ml(String label, String hint) {
        return new String[]{label, hint, "multi"};
    }

    // ---------- expression evaluator (shunting-yard) ----------
    static double evalExpr(String src) throws Exception {
        final String s = src.toLowerCase(Locale.US).replace("π", "pi").replace(",", "");
        final int[] pos = {0};
        class P {
            double parse() throws Exception {
                double v = parseAdd();
                if (pos[0] < s.length()) throw new Exception("Unexpected: " + s.charAt(pos[0]));
                return v;
            }

            double parseAdd() throws Exception {
                double v = parseMul();
                while (true) {
                    skip();
                    if (pos[0] < s.length() && (s.charAt(pos[0]) == '+' || s.charAt(pos[0]) == '-')) {
                        char op = s.charAt(pos[0]++);
                        double r = parseMul();
                        v = op == '+' ? v + r : v - r;
                    } else return v;
                }
            }

            double parseMul() throws Exception {
                double v = parsePow();
                while (true) {
                    skip();
                    if (pos[0] < s.length() && (s.charAt(pos[0]) == '*' || s.charAt(pos[0]) == '/' || s.charAt(pos[0]) == '%')) {
                        char op = s.charAt(pos[0]++);
                        double r = parsePow();
                        if ((op == '/' || op == '%') && r == 0) throw new Exception("Division by zero");
                        v = op == '*' ? v * r : op == '/' ? v / r : v % r;
                    } else return v;
                }
            }

            double parsePow() throws Exception {
                double base = parseUnary();
                skip();
                if (pos[0] < s.length() && s.charAt(pos[0]) == '^') {
                    pos[0]++;
                    return Math.pow(base, parsePow());
                }
                return base;
            }

            double parseUnary() throws Exception {
                skip();
                if (pos[0] < s.length() && s.charAt(pos[0]) == '-') {
                    pos[0]++;
                    return -parseUnary();
                }
                if (pos[0] < s.length() && s.charAt(pos[0]) == '+') {
                    pos[0]++;
                    return parseUnary();
                }
                return parseAtom();
            }

            double parseAtom() throws Exception {
                skip();
                if (pos[0] >= s.length()) throw new Exception("Unexpected end");
                char c = s.charAt(pos[0]);
                if (c == '(') {
                    pos[0]++;
                    double v = parseAdd();
                    skip();
                    if (pos[0] >= s.length() || s.charAt(pos[0]) != ')') throw new Exception("Missing )");
                    pos[0]++;
                    return v;
                }
                // function / constant
                String name = "";
                int start = pos[0];
                while (pos[0] < s.length() && Character.isLetter(s.charAt(pos[0]))) {
                    name += s.charAt(pos[0]++);
                }
                if (name.length() > 0) {
                    if (name.equals("pi")) return Math.PI;
                    if (name.equals("e")) return Math.E;
                    skip();
                    if (pos[0] >= s.length() || s.charAt(pos[0]) != '(') throw new Exception("Unknown: " + name);
                    pos[0]++;
                    double arg = parseAdd();
                    skip();
                    if (pos[0] >= s.length() || s.charAt(pos[0]) != ')') throw new Exception("Missing )");
                    pos[0]++;
                    if (name.equals("sin")) return Math.sin(Math.toRadians(arg));
                    if (name.equals("cos")) return Math.cos(Math.toRadians(arg));
                    if (name.equals("tan")) return Math.tan(Math.toRadians(arg));
                    if (name.equals("asin")) return Math.toDegrees(Math.asin(arg));
                    if (name.equals("acos")) return Math.toDegrees(Math.acos(arg));
                    if (name.equals("atan")) return Math.toDegrees(Math.atan(arg));
                    if (name.equals("ln")) return Math.log(arg);
                    if (name.equals("log")) return Math.log10(arg);
                    if (name.equals("sqrt")) return Math.sqrt(arg);
                    if (name.equals("abs")) return Math.abs(arg);
                    if (name.equals("round")) return Math.round(arg);
                    if (name.equals("floor")) return Math.floor(arg);
                    if (name.equals("ceil")) return Math.ceil(arg);
                    throw new Exception("Unknown function: " + name);
                }
                // number
                int st = pos[0];
                while (pos[0] < s.length() && (Character.isDigit(s.charAt(pos[0])) || s.charAt(pos[0]) == '.')) pos[0]++;
                if (st == pos[0]) throw new Exception("Unexpected: " + c);
                return Double.parseDouble(s.substring(st, pos[0]));
            }

            void skip() {
                while (pos[0] < s.length() && Character.isWhitespace(s.charAt(pos[0]))) pos[0]++;
            }
        }
        double r = new P().parse();
        if (Double.isNaN(r) || Double.isInfinite(r)) throw new Exception("Result is not a number");
        return r;
    }

    static void register() {
        // Basic + Scientific
        Compute.reg("Basic Calculator", new String[][]{fl("EXPRESSION", "e.g. 12*(3+4)/2")}, "=", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                double r = evalExpr(Compute.s(in, "f0"));
                return Compute.s(in, "f0") + "  =\n\n" + Ui.fmt(r);
            }
        });
        Compute.reg("Scientific Calculator", new String[][]{fl("EXPRESSION", "sin cos tan ln log sqrt ^ pi e")}, "=", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                double r = evalExpr(Compute.s(in, "f0"));
                return Compute.s(in, "f0") + "  =\n\n" + Ui.fmt(r) + "\nSci: " + String.format(Locale.US, "%.6e", r);
            }
        });

        // Age Calculator
        Compute.reg("Age Calculator", new String[][]{fl("BIRTHDATE", "yyyy-MM-dd"), fl("ON DATE", "yyyy-MM-dd (blank = today)")}, "CALCULATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
                java.util.Calendar b = new java.util.GregorianCalendar();
                b.setTime(f.parse(Compute.s(in, "f0")));
                java.util.Calendar n = new java.util.GregorianCalendar();
                String od = Compute.s(in, "f1");
                if (od.length() > 0) n.setTime(f.parse(od));
                int y = n.get(java.util.Calendar.YEAR) - b.get(java.util.Calendar.YEAR);
                int m = n.get(java.util.Calendar.MONTH) - b.get(java.util.Calendar.MONTH);
                int d = n.get(java.util.Calendar.DAY_OF_MONTH) - b.get(java.util.Calendar.DAY_OF_MONTH);
                if (d < 0) {
                    m--;
                    d += b.getActualMaximum(java.util.Calendar.DAY_OF_MONTH);
                }
                if (m < 0) {
                    y--;
                    m += 12;
                }
                long days = (n.getTimeInMillis() - b.getTimeInMillis()) / 86400000L;
                return "Age: " + y + " years " + m + " months " + d + " days\n"
                        + "Total days: " + days + "\nTotal weeks: " + days / 7
                        + "\nNext birthday in: ~" + (365 - (days % 365)) + " days";
            }
        });

        // BMI
        Compute.reg("BMI Calculator", new String[][]{fl("WEIGHT KG", "e.g. 65"), fl("HEIGHT CM", "e.g. 170")}, "CALCULATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                double w = Compute.d(in, "f0"), h = Compute.d(in, "f1") / 100.0;
                if (w <= 0 || h <= 0) throw new Exception("Enter valid values");
                double bmi = w / (h * h);
                String cat;
                if (bmi < 18.5) cat = "Underweight";
                else if (bmi < 25) cat = "Normal";
                else if (bmi < 30) cat = "Overweight";
                else cat = "Obese";
                return "BMI: " + Ui.fmt(bmi, 1) + "  (" + cat + ")\n"
                        + "Healthy weight range: " + Ui.fmt(18.5 * h * h, 1) + " - " + Ui.fmt(24.9 * h * h, 1) + " kg";
            }
        });

        // Loan / EMI
        Compute.reg("Loan / EMI", new String[][]{fl("AMOUNT", "Loan amount"), fl("RATE %/YEAR", "e.g. 8.5"),
                fl("MONTHS", "e.g. 60")}, "CALCULATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                double p = Compute.d(in, "f0"), r = Compute.d(in, "f1") / 1200.0, n = Compute.d(in, "f2");
                double emi = r == 0 ? p / n : p * r * Math.pow(1 + r, n) / (Math.pow(1 + r, n) - 1);
                return "Monthly EMI:   " + Ui.fmt(emi, 2) + "\n"
                        + "Total payment: " + Ui.fmt(emi * n, 2) + "\n"
                        + "Total interest:" + Ui.fmt(emi * n - p, 2);
            }
        });

        // Compound Interest
        Compute.reg("Compound Interest", new String[][]{fl("PRINCIPAL", "Amount"), fl("RATE %/YEAR", "e.g. 6"),
                fl("YEARS", "e.g. 10"), fl("COMPOUNDS/YEAR", "1 / 4 / 12 (blank = 1)")}, "CALCULATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                double p = Compute.d(in, "f0"), r = Compute.d(in, "f1") / 100.0, y = Compute.d(in, "f2");
                double n = Compute.s(in, "f3").length() == 0 ? 1 : Compute.d(in, "f3");
                double a = p * Math.pow(1 + r / n, n * y);
                return "Future value: " + Ui.fmt(a, 2) + "\nInterest:     " + Ui.fmt(a - p, 2);
            }
        });

        // Discount
        Compute.reg("Discount Calculator", new String[][]{fl("PRICE", "Original price"), fl("DISCOUNT %", "e.g. 20")}, "CALCULATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                double p = Compute.d(in, "f0"), d = Compute.d(in, "f1");
                return "You pay:   " + Ui.fmt(p * (1 - d / 100), 2) + "\nYou save:  " + Ui.fmt(p * d / 100, 2);
            }
        });

        // Percentage
        Compute.reg("Percentage Calc", new String[][]{fl("MODE", "of / increase / decrease / change"),
                fl("A", "first value"), fl("B", "second value")}, "CALCULATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                String m = Compute.s(in, "f0").toLowerCase(Locale.US);
                double a = Compute.d(in, "f1"), b = Compute.d(in, "f2");
                if (m.equals("of")) return Ui.fmt(a) + "% of " + Ui.fmt(b) + "  =  " + Ui.fmt(a * b / 100);
                if (m.equals("increase")) return Ui.fmt(a) + " + " + Ui.fmt(b) + "%  =  " + Ui.fmt(a * (1 + b / 100));
                if (m.equals("decrease")) return Ui.fmt(a) + " - " + Ui.fmt(b) + "%  =  " + Ui.fmt(a * (1 - b / 100));
                if (m.equals("change")) return "Change: " + Ui.fmt(b - a) + "\nPercent: " + Ui.fmt(a == 0 ? 0 : (b - a) / a * 100) + "%";
                throw new Exception("MODE must be of / increase / decrease / change");
            }
        });

        // GST / VAT
        Compute.reg("GST / VAT", new String[][]{fl("AMOUNT", "Price"), fl("TAX %", "e.g. 5 / 7 / 18")}, "CALCULATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                double a = Compute.d(in, "f0"), t = Compute.d(in, "f1");
                return "Net:    " + Ui.fmt(a, 2) + "\nTax:    " + Ui.fmt(a * t / 100, 2)
                        + "\nGross:  " + Ui.fmt(a * (1 + t / 100), 2);
            }
        });

        // Tip
        Compute.reg("Tip Calculator", new String[][]{fl("BILL", "Bill amount"), fl("TIP %", "e.g. 10"),
                fl("PEOPLE", "Split between (blank = 1)")}, "CALCULATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                double b = Compute.d(in, "f0"), t = Compute.d(in, "f1");
                int p = Compute.s(in, "f2").length() == 0 ? 1 : Compute.i(in, "f2");
                double tip = b * t / 100;
                return "Tip:      " + Ui.fmt(tip, 2) + "\nTotal:    " + Ui.fmt(b + tip, 2)
                        + "\nPer person: " + Ui.fmt((b + tip) / p, 2);
            }
        });

        // Date Difference
        Compute.reg("Date Difference", new String[][]{fl("FROM", "yyyy-MM-dd"), fl("TO", "yyyy-MM-dd")}, "CALCULATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
                long ms = f.parse(Compute.s(in, "f1")).getTime() - f.parse(Compute.s(in, "f0")).getTime();
                long days = ms / 86400000L;
                return "Days:  " + days + "\nWeeks: " + Ui.fmt(days / 7.0, 1) + "\nMonths: ~" + Ui.fmt(days / 30.44, 1)
                        + "\nYears: ~" + Ui.fmt(days / 365.25, 1);
            }
        });

        // Date Add / Sub
        Compute.reg("Date Add / Sub", new String[][]{fl("DATE", "yyyy-MM-dd"), fl("DAYS", "+ or - days, e.g. 45 or -30")}, "CALCULATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
                java.util.Calendar c = new java.util.GregorianCalendar();
                c.setTime(f.parse(Compute.s(in, "f0")));
                c.add(java.util.Calendar.DAY_OF_MONTH, Compute.i(in, "f1"));
                return "Result: " + f.format(c.getTime()) + "  ("
                        + new java.text.SimpleDateFormat("EEEE", Locale.US).format(c.getTime()) + ")";
            }
        });

        // Time Calculator
        Compute.reg("Time Calculator", new String[][]{fl("MODE", "add / sub"),
                fl("A (HH:MM:SS)", "e.g. 02:30:00"), fl("B (HH:MM:SS)", "e.g. 01:45:30")}, "CALCULATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                String[] pa = Compute.s(in, "f1").split(":");
                String[] pb = Compute.s(in, "f2").split(":");
                long a = sec(pa), b = sec(pb);
                long r = Compute.s(in, "f0").toLowerCase(Locale.US).startsWith("s") ? a - b : a + b;
                if (r < 0) r += 86400;
                return String.format(Locale.US, "%02d:%02d:%02d", r / 3600, (r % 3600) / 60, r % 60);
            }
        });

        // Fuel Cost
        Compute.reg("Fuel Cost", new String[][]{fl("DISTANCE KM", "e.g. 350"), fl("KM/L", "fuel efficiency"),
                fl("PRICE/L", "fuel price")}, "CALCULATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                double d = Compute.d(in, "f0"), e = Compute.d(in, "f1"), p = Compute.d(in, "f2");
                return "Fuel needed: " + Ui.fmt(d / e, 2) + " L\nTotal cost:  " + Ui.fmt(d / e * p, 2);
            }
        });

        // Salary
        Compute.reg("Salary Calculator", new String[][]{fl("MONTHLY SALARY", "e.g. 300000"), fl("DAYS/MONTH", "blank = 22")}, "CALCULATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                double m = Compute.d(in, "f0");
                double d = Compute.s(in, "f1").length() == 0 ? 22 : Compute.d(in, "f1");
                return "Per year:  " + Ui.fmt(m * 12, 0) + "\nPer month: " + Ui.fmt(m, 0)
                        + "\nPer day:   " + Ui.fmt(m / d, 0) + "\nPer hour:  " + Ui.fmt(m / d / 8, 0);
            }
        });

        // Income Tax (slab)
        Compute.reg("Income Tax", new String[][]{fl("ANNUAL INCOME", "e.g. 4800000"), fl("RATE %", "flat rate, e.g. 10")}, "CALCULATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                double inc = Compute.d(in, "f0"), rate = Compute.d(in, "f1");
                double tax = inc * rate / 100;
                return "Income:  " + Ui.fmt(inc, 0) + "\nTax (" + Ui.fmt(rate) + "%):  " + Ui.fmt(tax, 0)
                        + "\nNet:     " + Ui.fmt(inc - tax, 0) + "\n\n(Estimate only, check local rules)";
            }
        });

        // Bill Split
        Compute.reg("Bill Split", new String[][]{fl("TOTAL BILL", "amount"), fl("PEOPLE", "count")}, "SPLIT", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                double t = Compute.d(in, "f0");
                int p = Compute.i(in, "f1");
                return "Each person pays: " + Ui.fmt(t / p, 2);
            }
        });

        // Ratio
        Compute.reg("Ratio Calculator", new String[][]{fl("A", "value"), fl("B", "value")}, "CALCULATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                long a = Compute.L(in, "f0"), b = Compute.L(in, "f1");
                long g = gcd(a, b);
                return a + " : " + b + "  =  " + (a / g) + " : " + (b / g)
                        + "\nA/B = " + Ui.fmt((double) a / b, 4);
            }
        });

        // Fraction
        Compute.reg("Fraction Calc", new String[][]{fl("OPERATION", "add / sub / mul / div / simplify"),
                fl("A (n/d)", "e.g. 3/4"), fl("B (n/d)", "e.g. 1/2")}, "CALCULATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                String op = Compute.s(in, "f0").toLowerCase(Locale.US);
                long[] a = frac(Compute.s(in, "f1")), b = frac(Compute.s(in, "f2"));
                long n, d;
                if (op.equals("add")) {
                    n = a[0] * b[1] + b[0] * a[1];
                    d = a[1] * b[1];
                } else if (op.equals("sub")) {
                    n = a[0] * b[1] - b[0] * a[1];
                    d = a[1] * b[1];
                } else if (op.equals("mul")) {
                    n = a[0] * b[0];
                    d = a[1] * b[1];
                } else if (op.equals("div")) {
                    n = a[0] * b[1];
                    d = a[1] * b[0];
                } else {
                    n = a[0];
                    d = a[1];
                }
                if (d == 0) throw new Exception("Zero denominator");
                long g = gcd(Math.abs(n), Math.abs(d));
                return "Result: " + (n / g) + "/" + (d / g) + "  =  " + Ui.fmt((double) n / d, 6);
            }
        });

        // Random Number
        Compute.reg("Random Number", new String[][]{fl("MIN", "e.g. 1"), fl("MAX", "e.g. 100"), fl("COUNT", "how many (blank = 1)")}, "ROLL", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                long min = Compute.L(in, "f0"), max = Compute.L(in, "f1");
                int c = Compute.s(in, "f2").length() == 0 ? 1 : Compute.i(in, "f2");
                if (c < 1 || c > 1000) throw new Exception("Count must be 1-1000");
                Random r = new Random();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < c; i++) {
                    sb.append(min + (long) (r.nextDouble() * (max - min + 1)));
                    if (i < c - 1) sb.append(i % 8 == 7 ? '\n' : ' ');
                }
                return sb.toString();
            }
        });

        // Matrix Calculator
        Compute.reg("Matrix Calculator", new String[][]{fl("OPERATION", "det / inverse / multiply"),
                fl("A (2x2)", "a,b;c,d  e.g. 1,2;3,4"),
                fl("B (2x2)", "for multiply, e.g. 5,6;7,8")}, "CALCULATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                String op = Compute.s(in, "f0").toLowerCase(Locale.US);
                double[][] a = mat(Compute.s(in, "f1"));
                if (op.equals("det")) return "det(A) = " + Ui.fmt(a[0][0] * a[1][1] - a[0][1] * a[1][0]);
                if (op.equals("inverse")) {
                    double det = a[0][0] * a[1][1] - a[0][1] * a[1][0];
                    if (det == 0) throw new Exception("Matrix is singular");
                    double[][] iv = {{a[1][1] / det, -a[0][1] / det}, {-a[1][0] / det, a[0][0] / det}};
                    return "A⁻¹ =\n" + Ui.fmt(iv[0][0]) + "  " + Ui.fmt(iv[0][1]) + "\n" + Ui.fmt(iv[1][0]) + "  " + Ui.fmt(iv[1][1]);
                }
                if (op.equals("multiply")) {
                    double[][] b = mat(Compute.s(in, "f2"));
                    double[][] r = {{a[0][0] * b[0][0] + a[0][1] * b[1][0], a[0][0] * b[0][1] + a[0][1] * b[1][1]},
                            {a[1][0] * b[0][0] + a[1][1] * b[1][0], a[1][0] * b[0][1] + a[1][1] * b[1][1]}};
                    return "A × B =\n" + Ui.fmt(r[0][0]) + "  " + Ui.fmt(r[0][1]) + "\n" + Ui.fmt(r[1][0]) + "  " + Ui.fmt(r[1][1]);
                }
                throw new Exception("OPERATION must be det / inverse / multiply");
            }
        });

        // Quadratic
        Compute.reg("Quadratic Solver", new String[][]{fl("A", "x² coefficient"), fl("B", "x coefficient"), fl("C", "constant")}, "SOLVE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                double a = Compute.d(in, "f0"), b = Compute.d(in, "f1"), c = Compute.d(in, "f2");
                if (a == 0) throw new Exception("A cannot be 0");
                double disc = b * b - 4 * a * c;
                String s = "Discriminant: " + Ui.fmt(disc) + "\n";
                if (disc < 0) return s + "No real roots.\nComplex: x = " + Ui.fmt(-b / (2 * a)) + " ± " + Ui.fmt(Math.sqrt(-disc) / (2 * a)) + "i";
                double r1 = (-b + Math.sqrt(disc)) / (2 * a), r2 = (-b - Math.sqrt(disc)) / (2 * a);
                return s + "x₁ = " + Ui.fmt(r1) + "\nx₂ = " + Ui.fmt(r2);
            }
        });

        // Statistics
        Compute.reg("Statistics Calc", new String[][]{ml("NUMBERS", "one per line or comma separated")}, "CALCULATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                String[] toks = ComputeText.nv(in.get("f0")).split("[,\\s]+");
                List<Double> v = new ArrayList<>();
                for (String t : toks) if (t.trim().length() > 0) v.add(Double.parseDouble(t.trim()));
                if (v.size() == 0) throw new Exception("Enter numbers");
                double sum = 0;
                for (double x : v) sum += x;
                double mean = sum / v.size();
                java.util.Collections.sort(v);
                double med = v.size() % 2 == 1 ? v.get(v.size() / 2) : (v.get(v.size() / 2 - 1) + v.get(v.size() / 2)) / 2;
                double var = 0;
                for (double x : v) var += (x - mean) * (x - mean);
                var /= v.size();
                return "Count: " + v.size() + "\nSum:   " + Ui.fmt(sum) + "\nMean:  " + Ui.fmt(mean)
                        + "\nMedian: " + Ui.fmt(med) + "\nMin:   " + Ui.fmt(v.get(0)) + "\nMax:   " + Ui.fmt(v.get(v.size() - 1))
                        + "\nStdDev: " + Ui.fmt(Math.sqrt(var)) + "\nRange:  " + Ui.fmt(v.get(v.size() - 1) - v.get(0));
            }
        });

        // Prime Checker
        Compute.reg("Prime Checker", new String[][]{fl("NUMBER", "e.g. 2027")}, "CHECK", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                long n = Compute.L(in, "f0");
                if (n < 2) return n + " is NOT prime";
                boolean p = true;
                for (long i = 2; i * i <= n; i++) if (n % i == 0) {
                    p = false;
                    break;
                }
                StringBuilder sb = new StringBuilder(n + (p ? " IS PRIME" : " is NOT prime"));
                if (!p) {
                    for (long i = 2; i * i <= n; i++) if (n % i == 0) {
                        sb.append("\nSmallest factor: ").append(i).append("\n").append(n).append(" = ").append(i).append(" × ").append(n / i);
                        break;
                    }
                } else {
                    sb.append("\nNext prime: ");
                    long m = n + 1;
                    while (!isPrime(m)) m++;
                    sb.append(m);
                }
                return sb.toString();
            }
        });

        // Number Series
        Compute.reg("Number Series", new String[][]{fl("TYPE", "fibonacci / primes / squares / powers"),
                fl("COUNT", "how many terms, e.g. 10")}, "GENERATE", new Compute.Fn() {
            public String run(Map<String, String> in) throws Exception {
                String t = Compute.s(in, "f0").toLowerCase(Locale.US);
                int n = Compute.i(in, "f1");
                if (n < 1 || n > 30) throw new Exception("Count must be 1-30");
                StringBuilder sb = new StringBuilder();
                if (t.equals("fibonacci")) {
                    long a = 0, b = 1;
                    for (int i = 0; i < n; i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(a);
                        long nx = a + b;
                        a = b;
                        b = nx;
                    }
                } else if (t.equals("primes")) {
                    long p = 2;
                    int c = 0;
                    while (c < n) {
                        if (isPrime(p)) {
                            if (c > 0) sb.append(", ");
                            sb.append(p);
                            c++;
                        }
                        p++;
                    }
                } else if (t.equals("squares")) {
                    for (int i = 1; i <= n; i++) {
                        if (i > 1) sb.append(", ");
                        sb.append((long) i * i);
                    }
                } else if (t.equals("powers")) {
                    for (int i = 0; i < n; i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(1L << Math.min(i, 62));
                    }
                } else throw new Exception("TYPE must be fibonacci / primes / squares / powers");
                return sb.toString();
            }
        });
    }

    static boolean isPrime(long n) {
        if (n < 2) return false;
        for (long i = 2; i * i <= n; i++) if (n % i == 0) return false;
        return true;
    }

    static long gcd(long a, long b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    static long sec(String[] p) throws Exception {
        if (p.length < 1 || p.length > 3) throw new Exception("Use HH:MM:SS");
        long h = Long.parseLong(p[0]), m = p.length > 1 ? Long.parseLong(p[1]) : 0, s = p.length > 2 ? Long.parseLong(p[2]) : 0;
        return h * 3600 + m * 60 + s;
    }

    static long[] frac(String v) throws Exception {
        String[] p = v.split("/");
        if (p.length == 1) return new long[]{Long.parseLong(p[0].trim()), 1};
        if (p.length != 2) throw new Exception("Use n/d format");
        return new long[]{Long.parseLong(p[0].trim()), Long.parseLong(p[1].trim())};
    }

    static double[][] mat(String v) throws Exception {
        String[] rows = v.split(";");
        if (rows.length != 2) throw new Exception("Matrix must be 2 rows, e.g. 1,2;3,4");
        double[][] m = new double[2][2];
        for (int i = 0; i < 2; i++) {
            String[] cols = rows[i].split(",");
            if (cols.length != 2) throw new Exception("Each row needs 2 values");
            m[i][0] = Double.parseDouble(cols[0].trim());
            m[i][1] = Double.parseDouble(cols[1].trim());
        }
        return m;
    }
}

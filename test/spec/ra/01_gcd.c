int gcd(int a, int b) {
    while (a != 0) {
        int c;
        c = b % a;
        b = a;
        a = c;
    }
    return b;
}

int main() {
    gcd(3, 4);
}
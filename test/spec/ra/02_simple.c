int sum(int a, int b, int c, int d, int e, int f, int g, int h) {
    int tmpa = a + b;
    int tmpb = c + d;
    if(tmpa > tmpb) {
        return tmpa + tmpb;
    }else{
        return tmpa - tmpb;
    }
}

int main() {
    int ret = sum(1, 2, 3, 4,5,6,7,8);
    putint(ret);
    return 0;
}